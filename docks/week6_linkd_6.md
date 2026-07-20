# Неделя 6 — Аналитика на Mongo `linkd`

**Цель недели:** `GET /api/v1/links/{code}/stats` отдаёт totalClicks + разбивку по дням из Mongo, композитный ESR-индекс подтверждён `explain`, read-модель — осознанный CQRS-lite (что precomputed, что живой aggregation).
**Каждый день:** 1 build-задача (двигает проект) + 2 depth-промпта (открытые, вслух или письменно — из других категорий).

---

## День 1 — Схема Mongo + ESR-индекс

**Build**
- Подтверди схему из §6 design-doc: `click_events { shortCode, ts, ip?, ua?, referrer? }` (сырьё) и `link_stats { _id: shortCode, total, byDay: {...} }` (агрегат).
- Композитный индекс по правилу **ESR** (Equality, Sort, Range): `{ shortCode: 1, ts: 1 }` на `click_events`.
- Убедись, что consumer из W5 действительно пишет в обе коллекции: сырое событие в `click_events` и точечный `$inc` в `link_stats`.

**Depth**
1. *(concurrency)* Из W5 партиционирование Kafka по `aggregate_id=shortCode` гарантирует, что все события одной ссылки обрабатываются одним consumer-потоком последовательно. Почему это устраняет гонку на `$inc link_stats.total` для одного и того же `shortCode`?
2. *(k8s)* Чем `ConfigMap` отличается от `Secret`? Почему строка подключения к Mongo с паролем должна быть `Secret`, а не `ConfigMap`, даже если оба монтируются одинаково?

---

## День 2 — Атомарный инкремент + `GET /stats` (totalClicks)

**Build**
- Реализуй `GET /api/v1/links/{code}/stats`: пока только `totalClicks` — читай `link_stats.total` напрямую (это и есть read-модель, precomputed).
- Проверь атомарность: `$inc` на документ в MongoDB атомарен без транзакций (single-document write). Демонстрируй тестом: N параллельных кликов по одному коду → `total` точно равен N, без потерянных инкрементов.
- Обработай кейс отсутствия статистики (ссылка есть, кликов ещё не было) — верни `total: 0`, а не 404.

**Depth**
1. *(db/mongo)* Почему `$inc` над одним документом атомарен в MongoDB без явных транзакций — что гарантирует WiredTiger на уровне одного документа?
2. *(k8s)* Почему для Mongo (stateful) чаще используют `StatefulSet` + `PersistentVolumeClaim`, а не `Deployment`, которым описан сам stateless-сервис `linkd`?

---

## День 3 — Aggregation pipeline: разбивка по дням (`byDay`)

**Build**
- Добавь в `/stats` разбивку по дням: aggregation pipeline на `click_events` — `$match(shortCode)` → `$group(по дню из ts)` → `$sort`.
- Прогони `explain("executionStats")` на пайплайн — убедись, что `$match` использует индекс `{shortCode:1, ts:1}` (`IXSCAN`, а не `COLLSCAN`), и что `Equality` перед `Range` в индексе — не случайность.
- Сравни производительность с и без индекса на синтетических данных (сгенерируй тысячи `click_events`).

**Depth**
1. *(db/mongo)* Разложи по буквам правило ESR (Equality, Sort, Range): почему `{shortCode:1, ts:1}` эффективен для запроса «все клики по коду за диапазон дат, отсортированные по времени», а `{ts:1, shortCode:1}` — нет?
2. *(concurrency)* Document-level locking в MongoDB (WiredTiger) vs row-level locking в Postgres — заблокирует ли `$inc` на `link_stats` для `shortCode=A` конкурентный `$inc` для `shortCode=B`?

---

## День 4 — CQRS-lite: `$lookup` и осознанная staleness

**Build**
- Смоделируй сценарий, где нужен `$lookup`: например «клики по ссылкам одного владельца» — join `click_events`/`link_stats` с отдельной коллекцией `link_dim { shortCode, ownerId }` через `$lookup` в пайплайне.
- Зафиксируй в README осознанное решение CQRS-lite: `total` — precomputed (быстро, потенциально на миллисекунды устаревшее между кликом и инкрементом), `byDay` за произвольный диапазон — живой aggregation (точнее, но дороже). Опиши, когда каким путём идти.
- Добавь `TTL-индекс` (опционально) на `click_events`, если сырьё не нужно хранить вечно — обоснуй решение в README.

**Depth**
1. *(sys design)* CQRS-lite trade-off: precomputed `link_stats.total` (быстро, чуть устаревший) vs живой aggregation по `click_events` (точнее, дороже). По каким критериям выбирать, какой путь — read модель для API, а какой — для внутренней отчётности?
2. *(k8s)* Что такое `PodDisruptionBudget` и почему он важен именно для Mongo (`StatefulSet`) при voluntary node drain — в отличие от stateless-пода `linkd`, который просто пересоздастся?

---

## День 5 — Тесты, нагрузка на индекс, README

**Build**
- Testcontainers Mongo IT: `GET /stats` для кода без кликов → `0`; после N кликов → `total=N` и `byDay` совпадает по сумме; `$lookup`-сценарий возвращает корректный join.
- Убедись, что `explain` в README отражает актуальный индекс-скан (не COLLSCAN) на реалистичном объёме данных.
- Собери итоги недели: индекс (ESR), read-модель (что precomputed, что live), TTL-решение — по абзацу на каждое в README.

**Depth**
1. *(concurrency)* Ребалансировка consumer-группы Kafka (из W5) может произойти посреди обработки батча кликов. Как идемпотентный дизайн consumer'а (дедуп по event-id) защищает `link_stats.total` от двойного инкремента в этом случае?
2. *(k8s)* Чем `headless Service` отличается от обычного `ClusterIP Service`, и почему `StatefulSet` для Mongo обычно требует именно headless (связь со стабильными сетевыми именами подов)?

---

## Выходные — демо + отдых

- **Пятничное демо себе:** сделай десяток кликов по разным дням (можно подделать `ts` в тесте), запроси `/stats` — покажи `total` и `byDay`. 3 предложения: что работает, что шатко, что дальше.
- 1 запланированный выходной — часть плана, не провал.
- *(опционально, хороший день)* MongoDB docs: Aggregation Pipeline Optimization + Index Strategies (ESR) — закрепит теорию под практику этой недели.

---

## Итог недели (definition of done)

- [ ] `click_events` и `link_stats` заполняются consumer'ом из W5
- [ ] Композитный индекс `{shortCode:1, ts:1}` создан, ESR-порядок обоснован
- [ ] `GET /stats` отдаёт `totalClicks` (precomputed) и `byDay` (live aggregation)
- [ ] `explain` подтверждает `IXSCAN`, а не `COLLSCAN`, на обоих путях чтения
- [ ] `$lookup`-сценарий (join двух коллекций) реализован и протестирован
- [ ] CQRS-lite решение (что precomputed, что live) зафиксировано в README с обоснованием
- [ ] Integration-тесты (Testcontainers Mongo) зелёные
- [ ] 5 коммитов, 5 строк в стрик-логе

**Минимум плохого дня:** 25 минут build-блока + строка в лог. Стрик не рвём.

В конце недели скинь пайплайн aggregation и `explain`-вывод — разберу перед W7 (observability).
