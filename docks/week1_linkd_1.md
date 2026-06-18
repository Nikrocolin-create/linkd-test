# Неделя 1 — Скелет `linkd`

**Цель недели:** работающий create/resolve сервис на реальной Postgres, integration-тесты зелёные, lookup доказанно идёт по индексу.
**Каждый день:** 1 build-задача (двигает проект) + 2 depth-промпта (открытые, вслух или письменно — из других категорий).

---

## День 1 — Init + скелет

**Build**
- `spring init`: Web, Spring Data JPA, PostgreSQL Driver, Validation, Flyway, Testcontainers.
- Домен: `Link(id, shortCode, longUrl, createdAt)`.
- Заглушки `POST /links` (возвращает фейковый код) и `GET /{code}` (редирект на хардкод).
- Запусти, дёрни оба эндпоинта через curl/IntelliJ HTTP client. Коммит.

**Depth**
1. *(concurrency)* Объясни вслух за 3 минуты, что гарантирует happens-before и почему без него поток может крутиться на устаревшем значении.
2. *(db)* Почему плохая идея брать хэш длинного URL как первичный ключ? Минимум 2 причины.

---

## День 2 — Persistence на реальной Postgres

**Build**
- Flyway-миграция `V1__links.sql`: таблица `links`.
- JPA `@Entity` + `LinkRepository extends JpaRepository`.
- `POST` реально вставляет, `GET` реально читает по `shortCode`.
- Первый Testcontainers IT (как в примере выше): сохранить → найти. Зелёный. Коммит.

**Depth**
1. *(db)* Спроектируй индекс под `GET /{code}`. Какой тип, по какой колонке, будет ли covering? Запиши обоснование.
2. *(concurrency)* По памяти: как ConcurrentHashMap в Java 8+ достигает потокобезопасности (CAS + synchronized на голове бина)?

---

## День 3 — Генерация кода + чистые слои

**Build**
- base62-энкодер. Стратегия генерации: счётчик/sequence vs random — выбери и обоснуй в README.
- Обработка коллизий (если random).
- Рефактор в слои: `controller → service → repository`, всё через **constructor injection**, ноль field injection.

**Depth**
1. *(sys design)* Counter-based vs hash-based генерация ID: tradeoffs по предсказуемости, шардингу, коллизиям. Вслух.
2. *(spring)* Почему constructor injection > field injection? Перечисли 3 причины (final-поля, тесты без Spring, ловит циклы).

---

## День 4 — Валидация, ошибки, конфиг

**Build**
- Валидация входа: `@Valid`, проверка формата URL, длины.
- `@RestControllerAdvice` + ответы в формате problem+json (400 на кривой ввод).
- Вынеси настройки (base-url, длина кода) в `@ConfigurationProperties`-класс.
- Профили `local` / `test`.

**Depth**
1. *(spring)* `@ConfigurationProperties` vs `@Value` — когда что, почему первое типобезопаснее.
2. *(db)* Разница между unique constraint и unique index. Что создаётся под капотом при `UNIQUE`?

---

## День 5 — Тесты + индекс + замер

**Build**
- Расширь Testcontainers IT: дубликат кода, 404 на ненайденном, 400 на невалидном.
- Добавь индекс на `short_code` миграцией.
- Прогони `EXPLAIN ANALYZE` на запросе lookup, убедись что идёт **Index Scan**, а не **Seq Scan**. Сохрани вывод в README.

**Depth**
1. *(db)* Прочитай вывод `EXPLAIN ANALYZE`: чем Index Scan отличается от Seq Scan, что такое cost/rows/actual time.
2. *(concurrency)* Что будет, если два запроса одновременно сгенерят одинаковый код? Опиши гонку и как её ловит unique constraint + retry.

---

## Выходные — демо + отдых

- **Пятничное демо себе:** запусти сервис, создай ссылку, перейди по ней. 3 предложения: что работает, что шатко, что дальше.
- 1 запланированный выходной — это часть плана, не провал.
- *(опционально, хороший день)* DDIA глава 3 (Storage and Retrieval) — ляжет ровно на тему индексов этой недели.

---

## Итог недели (definition of done)

- [ ] `POST /links` создаёт, `GET /{code}` резолвит — на реальной Postgres
- [ ] Integration-тесты через Testcontainers зелёные (happy path + 404 + 400 + дубликат)
- [ ] Конфиг вынесен в `@ConfigurationProperties`, профили работают
- [ ] Lookup доказанно идёт по индексу (`EXPLAIN ANALYZE` в README)
- [ ] 5 коммитов, 5 строк в стрик-логе

**Минимум плохого дня:** 25 минут build-блока + строка в лог. Стрик не рвём.

В конце недели скинь репозиторий (или ключевые куски) — разберу как ревьюер перед W2 (кэш-слой).
