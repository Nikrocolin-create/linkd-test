# Неделя 4 — Rate limiting + resilience `linkd`

**Цель недели:** `POST /links` защищён token-bucket rate-limit'ом (сначала руками, потом Bucket4j+Redis), внешняя зависимость обёрнута circuit breaker'ом (Resilience4j), thread pool и connection pool посчитаны и настроены осознанно, а не «по умолчанию».
**Каждый день:** 1 build-задача (двигает проект) + 2 depth-промпта (открытые, вслух или письменно — из других категорий).

---

## День 1 — Token bucket руками

**Build**
- Реализуй token bucket **без библиотек**: класс с `AtomicLong tokens`, `AtomicLong lastRefillTimestamp`, метод `tryConsume()` с ленивым рефиллом (считаем, сколько токенов набежало со времени последнего обращения, вместо фонового потока-таймера).
- Примени на `POST /links` по ключу (пока по IP или фиксированному API-key-заглушке): превышение лимита → `429 Too Many Requests` + `Retry-After`.
- IT: N запросов подряд быстрее лимита → часть получает 429; подожди — токены восстановились → следующий проходит.

**Depth**
1. *(concurrency)* Объясни, как реализовать ленивый рефилл токенов через CAS-цикл на `AtomicLong` без блокировок и без фонового потока — что мешает гонке между «посчитать новые токены» и «списать токен»?
2. *(db)* Что такое advisory lock в Postgres (`pg_advisory_lock`)? Чем он отличается от row-lock и когда полезен (например, лидер-элекшн для одного крон-джоба на несколько инстансов)?

---

## День 2 — Bucket4j + Redis: распределённый лимит

**Build**
- Замени (или сравни рядом) ручную реализацию на **Bucket4j** с бэкендом на Redis (`bucket4j-redis`), чтобы лимит был общим между несколькими инстансами приложения, а не локальным per-JVM.
- Вынеси capacity/refill в `@ConfigurationProperties`.
- Обнови README: сравнение «руками vs Bucket4j+Redis» — что выиграл (консистентность между инстансами), что заплатил (round-trip в Redis на каждый запрос).

**Depth**
1. *(k8s)* Что такое readiness vs liveness probe? Почему недоступность Redis (от которого зависит rate-limit) должна валить readiness, а не liveness пода?
2. *(db)* Чем advisory lock отличается от обычного row-lock по времени жизни и видимости — переживает ли он commit транзакции?

---

## День 3 — Circuit breaker (Resilience4j)

**Build**
- Добавь стаб внешней зависимости — «safe-browsing» проверка URL перед созданием ссылки (HTTP-вызов на моковый/нестабильный эндпоинт, который можно программно ронять в тестах).
- Оберни вызов в Resilience4j `CircuitBreaker`: настрой failure-rate-threshold, sliding window, wait-duration-in-open-state, fallback-метод (например, пропустить проверку и залогировать warning, если breaker OPEN).
- IT: последовательные падения зависимости → breaker переходит в OPEN → дальнейшие вызовы идут в fallback без реального HTTP-запроса (проверь по счётчику вызовов).

**Depth**
1. *(sys design)* Опиши переходы состояний circuit breaker: CLOSED → OPEN → HALF_OPEN → CLOSED/OPEN. Почему HALF_OPEN пускает только ограниченное число пробных вызовов?
2. *(k8s)* Что произойдёт, если `readinessProbe` не настроен вовсе, а зависимость (Postgres/Redis) недоступна — как это скажется на балансировке трафика в `Service`, если под физически жив, но не может обслуживать запросы?

---

## День 4 — Thread pool и connection pool: считаем, а не гадаем

**Build**
- Посчитай целевой размер Tomcat thread pool по формуле Little's Law (`threads ≈ throughput × latency`) под целевые NFR из design-doc (`4 000 rps avg`, `p99 < 50мс`) — запиши расчёт в README и выставь `server.tomcat.threads.max` осознанно, а не дефолт.
- Аналогично посчитай размер HikariCP pool (формула `connections = ((core_count * 2) + effective_spindle_count)`, но для облачной БД — обычно меньше, чем кажется интуитивно) и выставь `spring.datasource.hikari.maximum-pool-size`.
- Нагрузочный mini-тест (`ExecutorService`, JUnit) с намеренно маленьким pool size → воспроизведи pool exhaustion (таймаут на получение коннекшена), увеличь до расчётного значения → таймауты уходят.

**Depth**
1. *(concurrency)* Выведи вслух формулу Little's Law применительно к thread pool: если p99 латентность 50мс и целевой throughput 4000 rps, сколько потоков нужно, и почему избыточно большой пул тоже вреден (context-switch overhead, memory)?
2. *(db)* Почему для HikariCP «меньше часто лучше»? Что происходит на стороне Postgres при слишком большом числе одновременных соединений (context switch, contention на internal locks)?

---

## День 5 — Наблюдаемость лимитов + тесты

**Build**
- Метрики через Micrometer: счётчик `ratelimit.rejected`, gauge/counter состояния circuit breaker (Resilience4j это даёт из коробки через `resilience4j.circuitbreaker.state`), таймер на пул коннекшений (`hikaricp.connections.pending`).
- Убедись, что всё видно через `/actuator/metrics` и `/actuator/prometheus`.
- Собери итоги: README-раздел «Resilience» — какие лимиты, почему такие числа, что покажет дашборд при перегрузке.

**Depth**
1. *(db)* Опиши на словах deadlock в Postgres: сценарий, где две транзакции держат разные локи и ждут друг друга — как Postgres его детектит и что делает (кого убивает)?
2. *(sys design)* Что такое bulkhead-паттерн и чем он дополняет circuit breaker? Почему отдельный thread pool на каждую внешнюю зависимость мешает одной медленной зависимости съесть все потоки?

---

## Выходные — демо + отдых

- **Пятничное демо себе:** прогони k6/JUnit-скрипт, который бьёт `POST /links` быстрее лимита — покажи 429 в логах, а затем принудительно урони стаб safe-browsing — покажи переход breaker в OPEN в метриках. 3 предложения: что работает, что шатко, что дальше.
- 1 запланированный выходной — часть плана, не провал.
- *(опционально, хороший день)* Resilience4j docs (Bulkhead, RateLimiter, TimeLimiter модули) — увидишь, что rate-limit и circuit breaker — лишь два из пяти паттернов resilience.

---

## Итог недели (definition of done)

- [ ] Token bucket реализован руками (CAS, ленивый рефилл) и покрыт тестом
- [ ] Bucket4j + Redis даёт распределённый rate-limit между инстансами
- [ ] Circuit breaker на стаб внешней зависимости: CLOSED/OPEN/HALF_OPEN воспроизведены в тесте
- [ ] Thread pool и HikariCP pool посчитаны по формуле, а не оставлены на дефолтах (расчёт в README)
- [ ] Pool exhaustion воспроизведён и устранён увеличением до расчётного размера
- [ ] Метрики rate-limit/circuit-breaker/pool видны через Actuator
- [ ] Integration-тесты зелёные (429, breaker-переходы, pool timeout)
- [ ] 5 коммитов, 5 строк в стрик-логе

**Минимум плохого дня:** 25 минут build-блока + строка в лог. Стрик не рвём.

В конце недели скинь расчёты пулов и код circuit breaker'а — разберу перед W5 (outbox → Kafka → consumer).
