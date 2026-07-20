# Неделя 8 — Kubernetes `linkd`

**Цель недели:** `linkd` задеплоен в kind/minikube по §10 design-doc — multistage-образ, Deployment/Service/ConfigMap/Secret, пробы, requests/limits, HPA, zero-downtime rolling update.
**Каждый день:** 1 build-задача (двигает проект) + 2 depth-промпта. Эта неделя — «лёгкие репы по всем темам»: половина промптов — быстрое повторение прошлых недель (интервальные повторения 1→3→7→21 день), половина — новый материал k8s.

---

## День 1 — Multistage Docker + distroless

**Build**
- Multistage `Dockerfile`: стадия сборки (Maven/Gradle + JDK) → стадия рантайма на distroless-базе (`gcr.io/distroless/java21-debian12` или аналог) — никаких shell/apt в финальном образе.
- Настрой JVM-флаги под контейнер: `-XX:MaxRAMPercentage`, `-XX:+UseContainerSupport` (в новых JDK — по умолчанию, но выставь осознанно) — без хардкода `-Xmx`.
- Собери образ, прогони локально (`docker run`), убедись, что все эндпоинты отвечают так же, как раньше.

**Depth**
1. *(concurrency, повтор W2/W4)* За 2 минуты вслух: как `ConcurrentHashMap` в Java 8+ достигает потокобезопасности и почему это дешевле, чем `synchronizedMap` целиком?
2. *(k8s)* Почему distroless-образ (без shell, без пакетного менеджера) снижает поверхность атаки? Что теряешь в отладке (нельзя `docker exec sh`) и чем это компенсируешь (ephemeral debug containers, `kubectl debug`)?

---

## День 2 — Deployment, Service, ConfigMap, Secret

**Build**
- Подними kind/minikube-кластер. Напиши манифесты: `Deployment` (образ из Дня 1), `Service` (ClusterIP) перед ним.
- Несекретный конфиг (base-url, TTL, лимиты) — в `ConfigMap`, смонтированном как переменные окружения или `application.yaml`-оверлей.
- Креды БД/Kafka/Redis/Mongo — в `Secret`; подключи внешние зависимости либо как сервисы в том же namespace (docker-compose-эквивалент манифестами), либо пробрось наружу через `host.docker.internal`/`kind` network — задокументируй выбор.

**Depth**
1. *(db, повтор W3)* За 2 минуты: как HASH-партиционирование `links` по `short_code` защищает уникальность, и что нужно не забыть в unique constraint?
2. *(k8s)* Секрет, смонтированный как переменная окружения, виден через `docker inspect`/`/proc/<pid>/environ` любому, кто получил доступ к поду. Почему монтирование `Secret` как файла в `volumeMount` в общем случае безопаснее переменных окружения?

---

## День 3 — Probes, requests/limits, QoS

**Build**
- `livenessProbe` (например, простой `/actuator/health/liveness`) — рестарт зависшего процесса.
- `readinessProbe` (`/actuator/health/readiness`, с кастомными health-индикаторами на Postgres/Redis/Kafka из W4/W7) — исключение из балансировки, пока зависимости недоступны.
- Выстави `resources.requests`/`resources.limits` осознанно (на основе расчётов thread pool/heap из W4) — проверь через `kubectl describe pod`, в какой QoS-класс (`Guaranteed`/`Burstable`/`BestEffort`) попал под.

**Depth**
1. *(jvm, повтор W3/W5)* За 2 минуты: что такое GC pause и почему промоушен объектов в Old generation коррелирует с его длительностью?
2. *(k8s)* Как `requests` и `limits` определяют QoS-класс пода, и почему именно `limits` (а не `requests`) — та величина, из-за превышения которой контейнер получает OOMKill?

---

## День 4 — HPA + rolling update

**Build**
- Настрой `HorizontalPodAutoscaler` по CPU (или, если есть время, по custom-метрике через Prometheus adapter — например, rps из W7).
- Нагрузи `k6`-скриптом до срабатывания HPA — зафиксируй в README, сколько времени занял scale-up, сколько реплик добавилось.
- Настрой rolling update: `maxUnavailable=0, maxSurge=1` — задеплой новую версию образа **во время** k6-нагрузки, убедись что запросы не проваливаются (readinessProbe отрабатывает раньше, чем старый под убит).

**Depth**
1. *(sys design, повтор W4/W5)* За 2 минуты: опиши состояния circuit breaker CLOSED/OPEN/HALF_OPEN или проблему dual-write, решённую outbox-паттерном — выбери то, что хуже помнишь.
2. *(k8s)* Как HPA принимает решение о масштабировании (period опроса `metrics-server`, cooldown на scale-down)? Почему custom-метрикам (не CPU/memory) нужен отдельный adapter?

---

## День 5 — Проверка под нагрузкой + README-runbook

**Build**
- Убей под руками (`kubectl delete pod`) во время активной k6-нагрузки — убедись, что `Service` перенаправляет трафик на оставшиеся поды без ошибок клиента.
- Повтори rolling update под нагрузкой ещё раз, теперь замерь p99 latency во время выката (из дашборда W7) — не должно быть заметного скачка, если `maxUnavailable=0`.
- README: «k8s runbook» — как задеплоить, как читать `kubectl describe pod` при проблеме, что означает каждый QoS-класс для отладки.

**Depth**
1. *(spring, повтор W2)* За 2 минуты: порядок вызовов в жизненном цикле Spring-бина (`BeanFactoryPostProcessor` → `BeanPostProcessor` → `@PostConstruct` → `InitializingBean`) — зачем он так устроен под capot readiness-проверки на старте?
2. *(k8s)* Как `maxUnavailable=0, maxSurge=1` вместе с readinessProbe гарантируют zero-downtime deploy? Что произойдёт, если readinessProbe настроен слишком «оптимистично» (возвращает 200 раньше, чем приложение реально готово)?

---

## Выходные — демо + отдых

- **Пятничное демо себе:** запусти k6-нагрузку, на её фоне сделай rolling update и убей один под руками — покажи, что дашборд W7 не показывает провала p99/ошибок. 3 предложения: что работает, что шатко, что дальше.
- 1 запланированный выходной — часть плана, не провал.
- *(опционально, хороший день)* Kubernetes docs: Pod Lifecycle + Probes — закрепит нюансы readiness/liveness timing из Дня 5.

---

## Итог недели (definition of done)

- [ ] Multistage distroless-образ собирается и запускается
- [ ] `Deployment`/`Service`/`ConfigMap`/`Secret` в kind/minikube, приложение отвечает через `Service`
- [ ] `livenessProbe`/`readinessProbe` реагируют на реальное состояние зависимостей (Postgres/Redis/Kafka)
- [ ] `requests`/`limits` выставлены осознанно, QoS-класс проверен
- [ ] HPA срабатывает под нагрузкой, зафиксировано время scale-up
- [ ] Rolling update (`maxUnavailable=0, maxSurge=1`) под нагрузкой проходит без ошибок и без скачка p99
- [ ] README: k8s runbook
- [ ] 5 коммитов, 5 строк в стрик-логе

**Минимум плохого дня:** 25 минут build-блока + строка в лог. Стрик не рвём.

В конце недели скинь манифесты и график p99 во время rolling update — разберу перед W9 (нагрузочное тестирование и harden).
