# Ответы к вопросам по заметке (1) 23-6-26

**1. `@URL`.** Аннотация Hibernate Validator (`org.hibernate.validator.constraints.URL`), не из javax/jakarta. Внутри по умолчанию использует `java.net.URL` — то есть проверяет разбираемость строки, а не «разумность» ссылки. Пройдут `http://localhost`, `ftp://...`, а `javascript:alert(1)` не пройдёт только потому, что это неизвестный протокол для `URL`. Ограничить схемы: `@URL(protocol = "https")` или regexp-флаг. `@Pattern` даёт полный контроль, но регулярка для URL быстро становится нечитаемой и дырявой. 
Практика: `@URL` + собственная проверка whitelist схем (`http`, `https`) и запрет на приватные адреса (SSRF).

**2. `@ControllerAdvice`.** Бин, чьи `@ExceptionHandler`/`@InitBinder`/`@ModelAttribute` применяются ко всем контроллерам в контексте. Сузить: `@ControllerAdvice(basePackages = ...)`, `assignableTypes = ...`, `annotations = ...`. `@RestControllerAdvice` = `@ControllerAdvice` + `@ResponseBody`, то есть возвращаемое значение сериализуется в тело ответа, а не трактуется как имя view. Для REST — всегда второй.

**3. Какое исключение когда.** `MethodArgumentNotValidException` — при `@Valid`/`@Validated` на `@RequestBody`-аргументе (валидация объекта после биндинга). `ConstraintViolationException` — при валидации отдельных параметров (`@RequestParam`, `@PathVariable`) в классе, помеченном `@Validated`. В Spring 6.1+ появился `HandlerMethodValidationException` — унифицированный вариант для параметров метода. Чтобы валидация вообще включилась, нужен `spring-boot-starter-validation` (Hibernate Validator в classpath) и `@Valid` перед `@RequestBody`.

**4. Достать ошибки полей.**
```java
Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
    .collect(Collectors.toMap(FieldError::getField,
                              f -> Objects.requireNonNullElse(f.getDefaultMessage(), "invalid"),
                              (a, b) -> a));
```
Не забыть про `getGlobalErrors()` — ошибки уровня объекта (кросс-полевые проверки).

**5. `ProblemDetail` / RFC 9457.** Стандартный формат тела ошибки с media type `application/problem+json`. Поля: `type` (URI-идентификатор типа проблемы, по умолчанию `about:blank`), `title` (короткое человекочитаемое имя), `status`, `detail` (описание конкретного случая), `instance` (URI конкретного вхождения). Расширения — через `problem.setProperty("errors", errorsMap)`. RFC 9457 — обновление RFC 7807.

**6. `forStatusAndDetail` vs `ResponseEntityExceptionHandler`.** Фабричный метод просто собирает объект — вы сами решаете, где и как его вернуть. Наследование от `ResponseEntityExceptionHandler` даёт готовые хендлеры для ~15 стандартных Spring MVC исключений (включая `MethodArgumentNotValidException`, `HttpMessageNotReadableException`, `NoHandlerFoundException`), которые можно точечно переопределять. `spring.mvc.problemdetails.enabled=true` включает автоматическую отдачу ProblemDetail для этих встроенных исключений вообще без своего `@ControllerAdvice`.

**7. `@ConfigurationProperties(prefix = "mail")`.** Поле `hostName` связывается с ключом `mail.host-name` (работает relaxed binding: `hostName`, `host-name`, `HOST_NAME` — всё одно). Включение: либо `@Component` на самом классе, либо `@EnableConfigurationProperties(MailProps.class)`, либо `@ConfigurationPropertiesScan` на главном классе. Плюсы против `@Value`: типобезопасность, группировка связанных настроек, поддержка вложенных объектов и списков, валидация, метаданные для автодополнения в IDE.

**8. Иммутабельность и валидация.** Да — `record MailProps(String hostName, int port) {}` или класс с одним конструктором (constructor binding). На класс вешается `@Validated`, на поля — обычные jakarta-аннотации (`@NotBlank`, `@Min`). Если значение не пройдёт проверку, контекст не поднимется: приложение упадёт на старте с `BindValidationException` — это «fail fast» и это хорошо, лучше упасть при деплое, чем на первом запросе.

**9. Почему 302 для редиректа.** 301 (Moved Permanently) браузер агрессивно кэширует — после первого перехода он больше не пойдёт на ваш сервер, и вы потеряете и счётчик кликов, и возможность отозвать/изменить ссылку. 302 (Found) по умолчанию не кэшируется, каждый переход проходит через сервис. 307/308 — то же самое, но с гарантией сохранения HTTP-метода; для GET-редиректа короткой ссылки разницы нет. Итог: 302 (или 307) для трекинга, 301 — только если аналитика не нужна и нужна скорость/разгрузка.

**10. `location(URI.create(longUrl))`.** Ставит заголовок `Location`; вместе со статусом 3xx это заставляет клиента перейти по адресу. Риски: (а) `URI.create` бросит `IllegalArgumentException` на невалидной строке — нужен контроль на этапе сохранения, а не редиректа; (б) open redirect — если хранить чужие ссылки без проверки схемы, сервис становится инструментом фишинга; (в) `javascript:`/`data:` схемы; (г) CRLF-инъекция в заголовок (современный Tomcat это режет, но валидировать на входе всё равно надо). Правильно: валидировать и нормализовать URL при создании ссылки, хранить уже безопасное значение.

**11. Единый формат ошибок.** Один `@RestControllerAdvice`, наследующий `ResponseEntityExceptionHandler`, плюс приватный метод-фабрика `problem(status, title, detail)`. Отдельные `@ExceptionHandler` для доменных исключений (`LinkNotFoundException` → 404), и финальный `@ExceptionHandler(Exception.class)` → 500 с обезличенным сообщением. Spring выбирает самый специфичный хендлер по иерархии типов.

**12. Невалидный JSON.** `HttpMessageNotReadableException` → **400 Bad Request**: тело синтаксически нечитаемо, до валидации дело не дошло. 422 Unprocessable Entity уместен, когда JSON разобран, но семантически неверен — и это как раз кейс `MethodArgumentNotValidException`. Единого мнения нет: многие API отдают 400 и там, и там. Главное — быть последовательным и описать выбор в документации.

**13. Локализация.** Сообщения кладутся в `ValidationMessages.properties` (Hibernate Validator) или в `messages.properties` со ссылкой вида `@NotBlank(message = "{link.url.required}")`. Spring подставит значения через `MessageSource`; параметры — `{0}`, `{min}`, `{max}`. Язык определяется по `Accept-Language` через `LocaleResolver`. В хендлере ошибку резолвят через `messageSource.getMessage(fieldError, locale)`.

**14. Не протечь наружу.** `server.error.include-stacktrace=never` (дефолт в Boot — `never`), `include-message=never`, `include-binding-errors=never` для продакшена. Стектрейс — в лог с correlation id; клиенту — `detail` без внутренних деталей плюс тот же id, чтобы можно было связать обращение с логом. Никогда не отдавать наружу сообщения исключений БД: они раскрывают имена таблиц и колонок.

**15. Тестирование advice.** `@WebMvcTest(LinkController.class)` поднимает только веб-слой; `@ControllerAdvice` подхватывается автоматически (при необходимости — `@Import(GlobalExceptionHandler.class)`). Проверка:
```java
mockMvc.perform(post("/links").contentType(APPLICATION_JSON).content("{\"url\":\"\"}"))
    .andExpect(status().isBadRequest())
    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
    .andExpect(jsonPath("$.status").value(400))
    .andExpect(jsonPath("$.errors.url").exists());
```
