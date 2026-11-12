# Task Manager (TaskAPI)

Сквозной учебный проект: менеджер задач на **Spring Boot 3** - от Spring Core до Docker.
REST API с JWT-аутентификацией, веб-интерфейс на Thymeleaf, миграции БД, юнит- и интеграционные тесты, `docker compose`.

**Стек:** Java 17+ (в Docker - 21), Spring Boot 3.5, Spring Web / Data JPA / Security / AOP / Validation, JJWT 0.12,
Thymeleaf, Flyway (и Liquibase), H2 (разработка) / PostgreSQL 16 (Docker), springdoc (Swagger UI), JUnit 5, Mockito, Maven.

Каждая часть технического задания - отдельный коммит и тег (`git tag -l`), см. [историю по частям](#история-по-частям-тз).

---

## Быстрый старт (Docker)

Нужен только Docker (Docker Desktop или Docker Engine + Compose v2).

```bash
git clone <URL-репозитория> task-manager && cd task-manager
cp .env.example .env            # заполните пароли и JWT_SECRET (без .env тоже запустится - со значениями по умолчанию)
docker compose up --build       # первая сборка занимает несколько минут
```

Приложение: <http://localhost:8080> - когда в логе появится `Started Application in X seconds`.

| Что | Адрес |
|---|---|
| Веб-интерфейс (форма входа) | <http://localhost:8080/login> |
| Swagger UI (кнопка **Authorize** - вставить `accessToken`) | <http://localhost:8080/swagger-ui.html> |
| REST API | `http://localhost:8080/tasks`, `/auth` (то же под `/api/...`) |
| PostgreSQL (только с этого компьютера) | `localhost:5432`, БД/пользователь/пароль из `.env` |

Тестовый администратор создаётся при старте: `ADMIN_EMAIL` / `ADMIN_PASSWORD` из `.env`
(по умолчанию `admin@example.com` / `admin12345` - **смените в `.env`**).

### Проверка по шагам (curl)

```bash
# 1. регистрация (роль USER) -> accessToken + refreshToken
curl -s -X POST localhost:8080/auth/register -H 'Content-Type: application/json' \
     -d '{"email":"user@test.com","password":"password123"}'

# 2. вход
curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
     -d '{"email":"user@test.com","password":"password123"}'
TOKEN=<accessToken из ответа>

# 3. задачи
curl -s -X POST localhost:8080/tasks -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
     -d '{"title":"Купить молоко","priority":"HIGH"}'
curl -s localhost:8080/tasks/my -H "Authorization: Bearer $TOKEN"
curl -i localhost:8080/tasks                      # без токена: 401 {"error":"Unauthorized",...}
```

То же одной командой: `./scripts/smoke-test.sh` (регистрация, вход, 3 задачи, `GET /tasks/my`, удаление как ADMIN, 401/403).
Готовая **Postman-коллекция**: [`postman/TaskManager.postman_collection.json`](postman/TaskManager.postman_collection.json)
(Import -> запускать папки по порядку; токены сохраняются скриптами).

### Остановка, очистка, логи, диагностика

```bash
docker compose up -d                  # запуск в фоне
docker compose logs -f app            # логи приложения (искать "Started Application")
docker compose ps                     # оба сервиса должны быть healthy
docker compose down                   # остановить и удалить контейнеры - ДАННЫЕ ОСТАЮТСЯ (volume postgres_data)
docker compose down -v                # то же + удалить volume: все данные стираются
docker compose up --build -d          # пересобрать образ после изменения кода

docker exec -it taskapi-postgres psql -U taskuser -d taskdb     # затем: \dt  (таблицы tasks, users, flyway_schema_history ...)
docker stats                          # CPU/RAM контейнеров
docker inspect taskapi-app            # секция Networks - IP контейнера
docker compose stop postgres          # намеренно остановить БД: запросы к API начнут падать, ошибка видна в логах app
```

**Данные переживают перезапуск:** `docker compose down` (без `-v`) -> `docker compose up -d` -> тот же логин работает,
задачи на месте (`./scripts/smoke-test.sh --expect-persisted`). Контейнеры stateless, данные живут в volume.

---

## Запуск без Docker (профиль dev)

Нужен JDK 17+ (Maven скачается через `./mvnw`).

```bash
./mvnw spring-boot:run
```

* БД - файловая H2 `./data/taskdb` (данные переживают перезапуск), схему создаёт Flyway, демо-задачи из `db/seed`.
* Консоль H2: <http://localhost:8080/h2-console> (JDBC URL `jdbc:h2:file:./data/taskdb`, пользователь `sa`, пароль пустой).
* Вход: <http://localhost:8080/login> -> `admin@example.com` / `admin12345` (создаётся при старте в профиле dev).
* Подробные логи транзакций и Flyway (только dev). Остальные профили: `docker`, `liquibase` (те же миграции через Liquibase), `test`.

```bash
./mvnw verify                                        # все тесты
./mvnw test -Dtest=TaskServiceTest                   # один класс
./mvnw spring-boot:run -Dspring-boot.run.profiles=liquibase   # миграции через Liquibase вместо Flyway
```

---

## API

Префиксы `/tasks` и `/api/tasks` (аналогично `/auth` и `/api/auth`) равнозначны: в ТЗ разных частей используются оба.
Все `/tasks/**` требуют заголовок `Authorization: Bearer <accessToken>`.

| Метод | URI | Ответ |
|---|---|---|
| POST | `/auth/register` | 200 `{token, accessToken, refreshToken}`; email занят - 409; невалидные данные - 400 |
| POST | `/auth/login` | 200 токены; неверный email или пароль - 401 |
| POST | `/auth/refresh` `{"refreshToken":"..."}` | 200 новая пара токенов (старый refresh-токен перестаёт работать); неизвестный или истёкший - 401 |
| POST | `/auth/logout` (нужен токен) | 204, refresh-токены пользователя отозваны |
| GET | `/tasks?page=0&size=10&sort=id&status=NEW&priority=HIGH` | 200 `{content, page, size, totalElements, totalPages, ...}` |
| GET | `/tasks/my` | 200, только задачи текущего пользователя |
| GET | `/tasks/{id}` | 200; нет - 404; чужая - 403 |
| POST | `/tasks` | 201 + `Location`; без названия - 400 с `fieldErrors` |
| PUT | `/tasks/{id}` | 200 / 404 / 403 |
| PATCH | `/tasks/{id}/status` `{"status":"DONE"}` | 200 / 404 / 403 |
| DELETE | `/tasks/{id}` | 204 (владелец или ADMIN) / 403 / 404 |
| GET | `/tasks/stats`, `/tasks/search?keyword=`, `/tasks/today` | статистика по статусам, поиск, созданные сегодня |

**Права:** `ADMIN` видит и меняет все задачи, обычный пользователь - только свои (`403` на чужие).
**Ошибки** всегда JSON: `{"status":404,"error":"Task not found","message":"...","fieldErrors":{...}}`;
401/403 от Spring Security - `{"error":"Unauthorized"|"Forbidden","status":...}`; просроченный токен - `{"error":"Token expired"}`.

**Веб-интерфейс** (Thymeleaf, форма логина + сессия + CSRF): `/login`, `/web/tasks` (список), `/web/tasks/new`,
`/web/tasks/{id}`, `/web/tasks/{id}/edit`; удалять в интерфейсе может только ADMIN.

---

## Как устроен проект

```
src/main/java/com/example
├── Application.java
├── annotation/    Loggable
├── aspect/        LoggingAspect (@Before/@After/@Around/@AfterThrowing), AnnotationLoggingAspect
├── config/        AppProperties, SecurityConfig (3 цепочки: H2, REST/JWT, веб), OpenApiConfig
├── controller/    TaskController, AuthController (REST) · TaskWebController, AuthWebController (Thymeleaf)
├── dto/           TaskRequest, TaskResponse, StatusUpdateRequest, PageResponse, ErrorResponse, Register/Login/RefreshToken..., AuthResponse
├── exception/     GlobalExceptionHandler (@ControllerAdvice) и исключения
├── model/         Task, Priority, Status, User, Role, RefreshToken, AuditLog
├── repository/    Spring Data JPA: Task, User, RefreshToken, AuditLog
├── runner/        AdminInitializer (тестовый ADMIN)
├── security/      JwtService, JwtAuthFilter, UserDetailsServiceImpl, SecurityExceptionHandler
└── service/       TaskService + TaskServiceImpl (@Transactional), AuthService, RefreshTokenService, AuditService, TaskStatsService
src/main/resources
├── application.yml                 профили dev (по умолчанию) и liquibase; JWT и приложение
├── application-docker.properties   профиль docker: PostgreSQL из переменных окружения
├── db/migration (V1-V6)            Flyway: схема       db/seed - демо-данные dev       db/changelog - то же для Liquibase
└── templates, static/css           Thymeleaf-страницы и стили
```

**Поток запроса (REST):** `JwtAuthFilter` читает токен -> `SecurityContext` -> `@PreAuthorize` -> `TaskController`
-> `TaskService` (проверка владельца, `@Transactional`) -> `TaskRepository`; исключения превращает в JSON `GlobalExceptionHandler`,
а вызовы сервиса логирует `LoggingAspect` (в самом сервисе кода логирования нет).

**Конфигурация (переменные окружения):**

| Переменная | Назначение |
|---|---|
| `JWT_SECRET` | секрет подписи JWT: Base64 (`openssl rand -base64 48`) или текст ≥ 32 символов; короче - приложение не стартует |
| `JWT_EXPIRATION` / `JWT_REFRESH_EXPIRATION` | жизнь access- и refresh-токена в мс (по умолчанию 24 часа / 7 дней; для 15 минут: `900000`) |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | PostgreSQL (профиль `docker`) |
| `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD` | тестовый ADMIN, создаётся при старте |

---

## Тесты и CI

`./mvnw verify` запускает все тесты на H2 (Docker не нужен):

| Тест | Что проверяет |
|---|---|
| `TaskServiceTest`, `AuthServiceTest` | юнит-тесты на Mockito без Spring-контекста |
| `JwtServiceTest` | токен, истёкший, подделанный, чужой секрет, слабый секрет |
| `TaskRepositoryTest`, `FlywayMigrationTest` | `@DataJpaTest` на настоящей H2 и миграции |
| `TransactionBehaviorTest` | откат при runtime/checked исключении, `REQUIRES_NEW` для аудита |
| `TaskControllerTest`, `AuthControllerTest`, `TaskWebControllerTest` | `@WebMvcTest`: статусы, JSON-ошибки, роли, CSRF, шаблоны |
| `AuthFlowIntegrationTest`, `WebFlowIntegrationTest` | сквозные сценарии: регистрация -> вход -> задачи -> права -> refresh -> logout (REST и веб) |

GitHub Actions (`.github/workflows/ci.yml`): тесты на JDK 17 и 21 + сборка образа, `docker compose up`,
`scripts/smoke-test.sh`, перезапуск без `-v` и проверка, что данные сохранились.

---

## История по частям ТЗ

| Часть | Тег | Главное |
|---|---|---|
| 1. Spring Core | `part-1-spring-core` | IoC/DI, `@Primary`, `@Profile`, prototype, жизненный цикл бинов |
| 2. REST | `part-2-rest-api` | `TaskController`, `@ControllerAdvice`, фильтры, статистика |
| 3. Spring Data JPA | `part-3-spring-data-jpa` | `JpaRepository`, derived queries, JPQL, пагинация |
| DTO + валидация | `part-3b-dto-validation` | `TaskRequest`/`TaskResponse`, `@Valid` |
| 4. AOP | `part-4-aop` | логирование и тайминг аспектами |
| 5. `@Transactional` | `part-5-transactional` | `readOnly`, `rollbackFor`, `REQUIRES_NEW` |
| 6. Миграции | `part-6-migrations` | Flyway + Liquibase |
| 7. Юнит-тесты | `part-7-unit-tests` | JUnit 5 + Mockito |
| 8-9. Security + JWT | `part-8-security-jwt`, `part-9-security-jwt` | два ТЗ на один шаг объединены в одну реализацию |
| 10. Thymeleaf | `part-10-thymeleaf` | веб-интерфейс, форма логина, роли в шаблонах |
| 11. Docker | `part-11-docker` | multi-stage Dockerfile, docker compose + PostgreSQL |

Подробности, схемы и таблицы по каждой части - в [`docs/parts.md`](docs/parts.md).
Посмотреть состояние на конкретном этапе: `git checkout part-3-spring-data-jpa` (вернуться: `git checkout main`).

### Решения там, где ТЗ противоречит само себе

* **Две версии Security + JWT (Части 8 и 9)** отличаются префиксами (`/api/...` и `/...`), ролями, кодами ошибок, сроком токенов и правилами удаления.
  Реализован объединённый вариант (обе версии путей работают; 409 на дубликат email; 401 на неверный refresh-токен;
  удаляет владелец или ADMIN) - таблица расхождений в [`docs/parts.md`](docs/parts.md#части-8-и-9-spring-security--jwt).
* **Секрет JWT из ТЗ Части 9** (`dGVzdFNlY3JldEtleVRoYXRJc0xvbmdFbm91Z2g=`) как Base64 даёт всего 29 байт (< 256 бит) - JJWT отверг бы его
  с `WeakKeyException`. Секрет принимается как Base64 (≥ 32 байт) **или как обычный текст ≥ 32 символов** (так работают и примеры из Части 11);
  всё короче отвергается при старте.
* **Демо-данные** лежат не в `V1`, а в `db/seed` и подключаются только профилем dev: схема не тащит за собой тестовые строки
  (в тестах и в Docker их нет).
* **Консольный `AppRunner` Части 1** удалён после появления Security (сервису нужен текущий пользователь); он сохранён в теге `part-1-spring-core`.
