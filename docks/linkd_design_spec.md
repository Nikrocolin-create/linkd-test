# linkd — Technical Design Document

**Статус:** Draft · **Автор:** Nikita · **Назначение:** источник правды для проекта (10-недельный план middle→senior).

Это учебный проект production-уровня: распределённый short-link сервис с аналитикой кликов. Цель — не «сделать шортенер», а пройти через все senior-заботы (кэш, идемпотентность, eventing, observability, k8s, нагрузка) на одном связном кодовом базисе.

---

## 1. Цели и не-цели

**Цели**
- Сократить длинный URL до короткого кода, отдавать редирект с низкой задержкой.
- Считать клики и отдавать аналитику.
- Спроектировать так, будто это реальный сервис под нагрузкой: кэш, rate limit, resilience, надёжная доставка событий, метрики, деплой в k8s.

**Не-цели (out of scope)**
- Полноценная авторизация/биллинг (owner_id — заглушка).
- Кастомные домены, UI, A/B-тесты ссылок.
- Гео-репликация между регионами (но дизайн не должен её исключать).

---

## 2. Функциональные требования

| # | Требование |
|---|---|
| F1 | Создать короткую ссылку из длинного URL |
| F2 | Поддержать опциональный кастомный alias |
| F3 | Поддержать опциональный TTL (срок жизни ссылки) |
| F4 | Редиректить `GET /{code}` на исходный URL (302) |
| F5 | Идемпотентное создание по `Idempotency-Key` |
| F6 | Считать клики асинхронно, не замедляя редирект |
| F7 | Отдавать аналитику по ссылке (всего кликов, по времени) |
| F8 | Возвращать корректные коды: 404 (нет), 410 (истекла), 409 (alias занят), 400/422 (валидация) |

---

## 3. Нефункциональные требования (с целевыми числами)

Числа — это **цели проектирования**, под которые инженерим, а не реальный трафик. Они дают senior-решениям смысл.

| Метрика | Цель | Что из неё следует |
|---|---|---|
| Доступность | 99.9% | резервирование, graceful degradation, probes |
| Latency редиректа (p99) | < 50 мс | кэш на горячем пути, индекс, без блокирующего I/O |
| Latency создания (p99) | < 150 мс | синхронная запись только в Postgres + outbox |
| Read:Write | ~100:1 | оптимизируем read-путь агрессивно |
| Корректность редиректа | 100% | код → URL никогда не путается |
| Durability ссылок | потеря недопустима | запись в БД до ответа клиенту |
| Durability кликов | допустима small loss | клики асинхронны, at-least-once |

---

## 4. Прикидка масштаба (back-of-envelope)

```
Создание:   100M ссылок/месяц ≈ 40 wps (avg), пик ×10 ≈ 400 wps
Чтение:     read:write 100:1   ≈ 4 000 rps (avg), пик ≈ 40 000 rps
Хранение:   ~500 байт/строка × 100M/мес = 50 ГБ/мес → ~3 ТБ за 5 лет
            → оправдывает партиционирование/шардинг на горизонте
Кэш:        ~20% ссылок дают ~80% чтений (Парето) → кэшируем горячий набор

Длина кода: base62^7 = 62^7 ≈ 3.5×10¹²  (7 символов с запасом)
            62^6 ≈ 5.7×10¹⁰ — впритык на 5 лет, берём 7
```

---

## 5. API

Базовый префикс: `/api/v1`. Формат ошибок — `application/problem+json` (RFC 7807).

### POST /api/v1/links — создать
```
Headers:  Idempotency-Key: <uuid>        (опционально, но рекомендуется)
Body:     { "url": "https://...", "customAlias": "promo", "ttlSeconds": 86400 }

201 Created
{ "shortCode": "aZ3kP9q", "shortUrl": "https://lnk.d/aZ3kP9q", "expiresAt": "..." }

400  невалидный URL
409  alias занят
422  семантическая ошибка (например ttl < 0)
```

### GET /{code} — резолв (горячий путь)
```
302 Found   Location: <longUrl>      + асинхронно фиксируем клик
404 Not Found
410 Gone     ссылка истекла
```

### GET /api/v1/links/{code}/stats — аналитика
```
200 OK
{ "shortCode": "...", "totalClicks": 1234, "byDay": [ {"date":"...","clicks":42}, ... ] }
```

### Ops
```
GET /actuator/health        liveness/readiness
GET /actuator/prometheus    метрики
```

---

## 6. Модель данных

### Postgres (источник правды)

```sql
-- links: основная таблица
id           BIGINT       PK (sequence-backed)
short_code   VARCHAR(16)  UNIQUE NOT NULL        -- индекс под F4
long_url     TEXT         NOT NULL
owner_id     BIGINT       NULL                   -- заглушка
created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
expires_at   TIMESTAMPTZ  NULL

-- idempotency: результат по ключу
key          UUID         PK
response     JSONB        NOT NULL
created_at   TIMESTAMPTZ  NOT NULL

-- outbox: событие клика до публикации (см. §7)
id           BIGINT       PK
aggregate_id VARCHAR(16)  NOT NULL               -- short_code
event_type   VARCHAR(32)  NOT NULL
payload      JSONB        NOT NULL
created_at   TIMESTAMPTZ  NOT NULL
published_at TIMESTAMPTZ  NULL
```

Индексы: `UNIQUE(short_code)`; частичный индекс `outbox(published_at) WHERE published_at IS NULL` под публикатор.

### MongoDB (read-модель аналитики)

```
click_events   { shortCode, ts, ip?, ua?, referrer? }        -- сырьё
link_stats     { _id: shortCode, total, byDay: {...} }        -- агрегат (CQRS-lite)
```
Композитный индекс по правилу **ESR**: `{ shortCode: 1 (E), ts: 1 (R) }`.

---

## 7. Архитектура

```
                         ┌──────────────────────────────────┐
   POST /links  ───────► │            linkd (Spring)         │
                         │  Controller → Service → Repo      │
                         │   │            │          │       │
                         │   │   idempotency-key      │       │
                         ▼   ▼            ▼          ▼        │
                       ┌─────────────────────────────────┐   │
   GET /{code}  ──┐    │   Postgres  (links + outbox)     │   │
                  │    └─────────────────────────────────┘   │
                  │           ▲ write-path (durable)          │
                  ▼           │                               │
            ┌───────────┐     │   ┌──────────────────────┐    │
            │  Redis    │◄────┘   │ Outbox Publisher      │    │
            │ cache-side│         │ (poll unpublished)    │    │
            └───────────┘         └──────────┬───────────┘    │
              read-path                      │ produce        │
            (p99 < 50ms)                     ▼                │
                                       ┌──────────┐           │
                                       │  Kafka   │           │
                                       └────┬─────┘           │
                                            │ consume         │
                                            ▼  (idempotent)   │
                                   ┌──────────────────┐       │
                                   │  Analytics worker │──────►│  MongoDB
                                   └──────────────────┘        (click_events, link_stats)
```

**Read-path (горячий):** `GET /{code}` → Redis (cache-aside) → при промахе Postgres → заполняем кэш → 302. Клик кладём в outbox в той же транзакции? Нет — клик не должен ронять редирект; пишем клик-событие отдельно, потеря допустима (NFR §3).

**Write-path:** `POST` → проверка idempotency-key → запись `links` → ответ. Durable до ответа.

**Event-path:** клики → outbox → publisher → Kafka → идемпотентный consumer → Mongo. **Outbox решает проблему dual-write:** нельзя атомарно записать в БД и в Kafka, поэтому пишем событие в ту же БД-транзакцию, а отдельный publisher дочитывает и шлёт в Kafka.

---

## 8. Ключевые решения (ADR-стиль)

**D1. Генерация кода — sequence + base62.**
Берём `nextval` из Postgres sequence, кодируем в base62. Альтернативы: random (риск коллизий, нужен retry-цикл), hash(url) (предсказуемость, коллизии). Sequence даёт гарантированную уникальность без коллизий ценой предсказуемости — для учебного сервиса приемлемо; в README отметить, что для anti-enumeration можно подмешать шифр-перестановку.

**D2. Кэширование — cache-aside + Redis, защита от stampede.**
Read-through дешевле в коде, но cache-aside даёт контроль. От cache stampede (тысячи промахов по одному горячему ключу при инвалидации) — request coalescing: только один поток идёт в БД, остальные ждут результат.

**D3. Идемпотентность создания.**
`Idempotency-Key` → сохранённый ответ. Повтор того же ключа возвращает тот же результат, не создавая дубль. Защищает от ретраев клиента (NFR, at-least-once на клиентской стороне).

**D4. Outbox для событий кликов.**
См. §7. Гарантирует, что событие не потеряется между БД и Kafka. Consumer идемпотентен (дедуп по event-id), потому что доставка at-least-once.

**D5. Resilience.**
Rate limit (token bucket) на создание — защита от абьюза. Circuit breaker на внешние вызовы (например стаб safe-browsing проверки) — чтобы падающая зависимость не каскадила.

**D6. Партиционирование (на будущее).**
При росте `links` — партиционирование/шардинг по `hash(short_code)`. Consistent hashing, чтобы добавление узла двигало ~1/N ключей. В MVP не реализуем, но схему не закладываем так, чтобы это потом стало невозможно.

---

## 9. Observability & SLO

- **Метрики (Micrometer → Prometheus):** rps, latency-гистограммы по эндпоинтам, cache hit ratio, lag outbox-публикатора, размер Kafka-консьюмер-лага.
- **Логи:** структурированные (JSON), с trace-id.
- **Трейсинг:** OpenTelemetry, сквозной trace по пути POST и по event-path.
- **SLO:** доступность 99.9%, redirect p99 < 50 мс, cache hit ratio > 80% на горячем наборе.

---

## 10. Деплой (Kubernetes)

- Multistage Docker, distroless-база, JVM-флаги под контейнер (`-XX:MaxRAMPercentage`).
- `Deployment` (stateless app) + `Service`.
- `ConfigMap` (несекретный конфиг) + `Secret` (креды БД/Kafka).
- `livenessProbe` (рестарт) + `readinessProbe` (трафик).
- `requests`/`limits` → QoS; `HorizontalPodAutoscaler` по CPU/custom-метрике.
- Rolling update: `maxUnavailable=0, maxSurge=1` (zero-downtime).
- Postgres/Kafka/Mongo/Redis — как внешние зависимости (или операторы); сам app — stateless.

---

## 11. Риски и открытые вопросы

- **Q1:** Где хранить idempotency-результаты — Postgres или Redis с TTL? (Postgres проще на старте.)
- **Q2:** Кликовый клиент теряет события при падении app до записи в outbox — приемлемо ли? (Да, по NFR.)
- **Q3:** Нужна ли строгая защита от перебора кодов? (В MVP нет; см. D1.)
- **R1:** Docker-pull за корпоративным прокси (Zscaler) на рабочей машине — может мешать Testcontainers/k8s-образам.

---

## 12. Фазы реализации (привязка к 10-недельному плану)

| Недели | Что закрываем из этого спека |
|---|---|
| 1 | §5 (create/resolve), §6 Postgres, индекс |
| 2 | §8 D2 — кэш-слой |
| 3 | §8 D1, D3 — генерация кода, идемпотентность, §8 D6 (дизайн шардинга) |
| 4 | §8 D5 — rate limit, circuit breaker |
| 5 | §7 event-path, §8 D4 — outbox → Kafka → consumer |
| 6 | §6 Mongo, §5 stats — аналитика, ESR-индексы |
| 7 | §9 — observability |
| 8 | §10 — Kubernetes |
| 9 | §3 NFR под нагрузкой — load test, профилирование, harden |
| 10 | этот документ → финализировать как design-doc, mock-интервью |

---

*Документ живой: по ходу проекта возвращайся и фиксируй принятые решения и причины. Умение вести и защищать такой спек — ровно то, что отличает senior от middle.*
