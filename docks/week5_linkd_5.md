# Неделя 5 — Outbox → Kafka → consumer `linkd`

**Цель недели:** клики по ссылке пишутся в outbox не замедляя редирект, publisher доставляет их в Kafka, идемпотентный consumer читает их без дублей — весь event-path из §7 design-doc живой и покрыт тестами.
**Каждый день:** 1 build-задача (двигает проект) + 2 depth-промпта (открытые, вслух или письменно — из других категорий).

---

## День 1 — Outbox-таблица + запись клика без замедления редиректа

**Build**
- Миграция `V_outbox.sql`: таблица `outbox(id, aggregate_id, event_type, payload JSONB, created_at, published_at NULL)` — как в §6 design-doc.
- На `GET /{code}`: после успешного резолва (кэш или Postgres) — асинхронно вставь строку в `outbox` (`event_type=LINK_CLICKED`, `aggregate_id=shortCode`, `payload={ts, ...}`), **не блокируя** ответ клиенту.
- Реши, как именно «не блокируя»: `@Async`-метод с отдельным executor'ом. Замерь, что редирект не ждёт записи клика (лог timestamp до/после).

**Depth**
1. *(spring)* Почему вызов `@Async`-метода **из того же класса** (self-invocation) не срабатывает асинхронно и выполняется синхронно в вызывающем потоке? Как это связано с тем, что Spring AOP — proxy-based?
2. *(jvm)* Что такое TLAB (thread-local allocation buffer) и почему он снижает contention при частых аллокациях в многопоточном коде (например, в executor'е, штампующем событие на каждый клик)?

---

## День 2 — Outbox publisher: poll unpublished → Kafka

**Build**
- Напиши publisher: периодически (например `@Scheduled(fixedDelay=...)`) читает батч строк `WHERE published_at IS NULL ORDER BY id LIMIT N`, отправляет в Kafka, помечает `published_at = now()` после подтверждения доставки.
- Добавь частичный индекс `outbox(created_at) WHERE published_at IS NULL` (уже упомянут в §6) — подтверди `EXPLAIN` что он используется.
- IT (Testcontainers Kafka): создай событие в outbox напрямую → дождись, пока publisher его заберёт и опубликует → проверь `published_at` проставлен.

**Depth**
1. *(spring)* Как под капотом работает `@Scheduled`: какой `TaskScheduler` используется по умолчанию (однопоточный!) и что произойдёт, если один прогон publisher'а не успевает закончиться до следующего тика — `fixedDelay` vs `fixedRate`?
2. *(jvm)* Что такое false sharing? Как конкурентные обновления `AtomicLong`-счётчиков (например, метрика «сколько строк опубликовано») на соседних полях одного кэш-лайна могут незаметно тормозить многопоточный код?

---

## День 3 — Kafka producer: идемпотентность и партиционирование по ключу

**Build**
- Настрой producer: `enable.idempotence=true`, `acks=all` — чтобы ретраи publisher'а на сетевой сбой не создавали дублей на уровне Kafka-протокола.
- Партиционируй по ключу `aggregate_id` (`shortCode`) — все события одной ссылки идут в одну партицию, сохраняя порядок кликов по ссылке.
- Создай топик `link-clicks` (столько партиций, сколько разумно для будущего масштабирования consumer-группы).

**Depth**
1. *(sys design)* Kafka гарантирует порядок только **внутри партиции**. Почему партиционирование по `aggregate_id=shortCode` критично именно здесь, а не по `event_type` или round-robin?
2. *(spring)* Как устроены Spring AOP-прокси (JDK dynamic proxy vs CGLIB)? Почему `@Transactional`/`@Async` не срабатывают на `private`-методах и при self-invocation — в обоих случаях причина одна?

---

## День 4 — Идемпотентный consumer → Mongo

**Build**
- Подними Mongo в `docker-compose.yml` (если ещё нет), подключи `spring-boot-starter-data-mongodb`.
- Consumer читает `link-clicks`, перед записью проверяет дедуп: например `processed_events(_id: eventId)` с unique-индексом в Mongo — если insert падает на дубликате, событие уже обработано, коммитим offset и идём дальше.
- После дедупа — пишем в `click_events` (сырьё) и точечно инкрементируем `link_stats` (агрегат) в той же логической операции.
- Ручной коммит offset **после** успешной записи (`enable.auto.commit=false`), чтобы at-least-once не превратился в at-most-once при падении между чтением и записью.

**Depth**
1. *(sys design)* Разница между at-least-once, at-most-once и exactly-once delivery. Почему идемпотентный consumer (дедуп по event-id) — практичный способ получить «эффективно ровно один раз» без распределённых транзакций?
2. *(jvm)* Если коммит offset делается из другого потока, чем обработка сообщения — какие гарантии видимости (happens-before) нужны, и как их обеспечивает внутренняя синхронизация Kafka-клиента?

---

## День 5 — End-to-end тест + observability outbox-лага

**Build**
- Полный IT: `GET /{code}` → строка в outbox → publisher → Kafka (Testcontainers) → consumer → `click_events`/`link_stats` в Mongo обновлены. Дождись через awaitility/polling, не `Thread.sleep` наугад.
- Метрика outbox-лага: gauge «сколько строк с `published_at IS NULL` старше X секунд» — сигнал, что publisher отстаёт или упал.
- README: зафиксируй, как решена dual-write проблема (D4) и что consumer идемпотентен по event-id, а не полагается на Kafka exactly-once.

**Depth**
1. *(spring)* По умолчанию `@Transactional` откатывает только на unchecked-исключениях. Почему это важно для *записи в outbox*: что случится, если бизнес-логика бросит checked-исключение после вставки outbox-строки в той же транзакции?
2. *(jvm)* Почему длинная stop-the-world GC-пауза на стороне consumer'а может привести к ребалансировке consumer-группы (превышение `session.timeout.ms`)? Как `max.poll.interval.ms` защищает от этого при долгой обработке сообщения?

---

## Выходные — демо + отдых

- **Пятничное демо себе:** сделай 5 `GET /{code}`, покажи, что редирект не тормозит, а через пару секунд `link_stats` в Mongo обновился. 3 предложения: что работает, что шатко, что дальше.
- 1 запланированный выходной — часть плана, не провал.
- *(опционально, хороший день)* «Designing Data-Intensive Applications», глава 11 (Stream Processing) — outbox/CDC и идемпотентные консьюмеры разбираются там подробно.

---

## Итог недели (definition of done)

- [ ] Клик пишется в outbox асинхронно, не замедляя `GET /{code}`
- [ ] Publisher вычитывает неопубликованные строки и шлёт в Kafka, помечает `published_at`
- [ ] Producer идемпотентен (`enable.idempotence=true`), партиционирование по `aggregate_id`
- [ ] Consumer дедуплицирует по event-id перед записью в Mongo, offset коммитится вручную после обработки
- [ ] End-to-end IT (Testcontainers Kafka + Mongo) зелёный
- [ ] Метрика outbox-лага видна через Actuator
- [ ] README: dual-write проблема и её решение (D4) описаны своими словами
- [ ] 5 коммитов, 5 строк в стрик-логе

**Минимум плохого дня:** 25 минут build-блока + строка в лог. Стрик не рвём.

В конце недели скинь end-to-end тест и метрику outbox-лага — разберу перед W6 (аналитика на Mongo).
