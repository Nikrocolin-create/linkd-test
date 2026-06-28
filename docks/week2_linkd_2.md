# Неделя 2 — Кэш-слой `linkd`

**Цель недели:** Redis cache-aside поверх реального Postgres, TTL настроен, stampede защищён (request coalescing), integration-тесты зелёные.
**Каждый день:** 1 build-задача (двигает проект) + 2 depth-промпта (открытые, вслух или письменно — из других категорий).

---

## День 1 — Redis в docker-compose + базовое подключение

**Build**
- Добавь `redis` в `docker/docker-compose.yml` (образ `redis:7-alpine`, порт 6379).
- Подключи `spring-boot-starter-data-redis` в `pom.xml`.
- Настрой `RedisTemplate<String, String>` через `@Configuration`-класс (host/port в `@ConfigurationProperties`).
- Напиши smoke-тест: `set("test", "ok")` → `get("test")` == `"ok"` через Testcontainers Redis. Зелёный. Коммит.

**Depth**
1. *(db)* Объясни вслух за 3 минуты: что такое уровни изоляции транзакций SQL (READ UNCOMMITTED, READ COMMITTED, REPEATABLE READ, SERIALIZABLE) и какую аномалию каждый закрывает.
2. *(concurrency)* Что такое happens-before в JMM? Почему `volatile`-запись happens-before `volatile`-чтению той же переменной?

---

## День 2 — Cache-aside на `GET /{code}`

**Build**
- Реализуй cache-aside в `LinkService.resolve(code)`:
  1. Проверь Redis → если hit → верни.
  2. Miss → читай Postgres → пиши в Redis с TTL → верни.
- TTL вынеси в `@ConfigurationProperties` (например, `linkd.cache.ttl=3600s`).
- Покрой Testcontainers IT: первый `GET` — miss (запись в кэш), второй — hit (Postgres не дёргается, можно проверить счётчиком вызовов через Mockito spy). Зелёный. Коммит.

**Depth**
1. *(sys design)* Объясни паттерны кэширования: cache-aside vs read-through vs write-through vs write-behind. Когда что применять, каков риск staleness в каждом?
2. *(db)* Что такое phantom read? На каком уровне изоляции он возможен и почему REPEATABLE READ его не закрывает в PostgreSQL (hint: MVCC)?

---

## День 3 — Инвалидация кэша + `DELETE /links/{code}`

**Build**
- Реализуй `DELETE /links/{code}`: удаляй из Postgres, затем вычищай ключ из Redis.
- Добавь `POST /links` cache warming — после вставки сразу пиши в Redis (write-through вариант).
- IT: создать → GET (hit) → DELETE → GET (miss, fallback на Postgres → 404). Коммит.

**Depth**
1. *(concurrency)* Что такое `AtomicReference`? Почему `compareAndSet` — это lock-free, а не wait-free?
2. *(spring)* Объясни жизненный цикл бина Spring: в каком порядке вызываются `BeanFactoryPostProcessor`, `BeanPostProcessor`, `@PostConstruct`, `InitializingBean`?

---

## День 4 — Stampede protection (request coalescing / dog-pile defense)

**Build**
- Смоделируй проблему: 100 потоков одновременно читают несуществующий ключ → 100 запросов в Postgres.
- Реализуй защиту через `setnx`-лок (Redis `SET key value NX EX`) или `ConcurrentHashMap<String, CompletableFuture<>>` в памяти:
  - Первый поток берёт лок, остальные ждут результата.
  - По окончании — снять лок, разбудить ждущих.
- IT: убедись, что при cache-miss под нагрузкой Postgres получает **не более 1 запроса** на ключ. Коммит.

**Depth**
1. *(sys design)* Cache stampede / dog-pile: опиши 3 стратегии защиты (request coalescing, probabilistic early expiration, external locking). Tradeoffs каждой.
2. *(db)* Чем `SELECT FOR UPDATE` отличается от `SELECT FOR SHARE`? Когда нужен пессимистичный лок вместо MVCC?

---

## День 5 — Observability кэша + тюнинг TTL

**Build**
- Добавь метрики через Micrometer (уже есть в Spring Boot Actuator):
  - Счётчики `cache.hits` и `cache.misses` с тегом `cacheName=links`.
  - Подключи `/actuator/metrics/cache.hits` — убедись, что значения растут при тестах.
- Напиши нагрузочный mini-тест (JUnit + `ExecutorService` на 50 потоков): замерь hit-rate при TTL=60s vs TTL=10s, запиши в README.
- Расширь IT: проверь, что после истечения TTL следующий `GET` делает Postgres-запрос (используй `Thread.sleep` или Testcontainers с коротким TTL). Коммит.

**Depth**
1. *(jvm)* Что такое GC roots? Почему объект в кэше (in-memory `HashMap`) не собирается GC, даже если на него нет других ссылок из бизнес-логики?
2. *(concurrency)* Объясни `volatile` vs `synchronized` vs `ReentrantLock`: когда достаточно `volatile`, а когда нужен монитор?

---

## Выходные — демо + отдых

- **Пятничное демо себе:** запусти Docker Compose, создай ссылку, сделай 5 GET-запросов — в логах должны быть видны hit/miss. 3 предложения: что работает, что шатко, что дальше.
- 1 запланированный выходной — это часть плана, не провал.
- *(опционально, хороший день)* DDIA глава 5 (Replication) или Redis documentation: Keyspace Notifications, eviction policies (LRU vs LFU vs noeviction).

---

## Итог недели (definition of done)

- [ ] Redis запущен в `docker-compose.yml`, подключён через `@ConfigurationProperties`
- [ ] `GET /{code}` — cache-aside: miss пишет в Redis с TTL, hit не трогает Postgres
- [ ] `DELETE /links/{code}` инвалидирует кэш
- [ ] Stampede protection: при cache-miss под нагрузкой Postgres получает 1 запрос на ключ
- [ ] Micrometer-метрики `cache.hits` / `cache.misses` видны через Actuator
- [ ] Integration-тесты через Testcontainers зелёные (hit, miss, invalidation, TTL expiry)
- [ ] TTL вынесен в конфиг, hit-rate замер в README
- [ ] 5 коммитов, 5 строк в стрик-логе

**Минимум плохого дня:** 25 минут build-блока + строка в лог. Стрик не рвём.

В конце недели скинь метрики hit-rate и код stampede-защиты — разберу перед W3 (ID-генерация + идемпотентность).
