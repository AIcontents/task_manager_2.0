# Task Manager (TaskAPI)

Сквозной учебный проект на Spring Boot 3: от Spring Core до Docker.
Каждая часть технического задания зафиксирована отдельным коммитом и тегом `part-N-...`
(`git tag -l`, `git checkout part-3-spring-data-jpa`).

## Часть 1. Spring Core

Консольное приложение (`CommandLineRunner`), хранение в памяти.
Концепции: IoC/DI, `@Component`, `@Configuration`/`@Bean`, `@Qualifier`, `@Primary`, `@Profile`,
`@Scope("prototype")`, `ObjectProvider`, `@Value`, `@PostConstruct`/`@PreDestroy`, `ApplicationContext`.

> После Части 2 консольный сценарий выключен по умолчанию. Включить:
> `./mvnw spring-boot:run -Dspring-boot.run.arguments="--app.runner.enabled=true --app.max-tasks=3"`

### Запуск

```bash
./mvnw spring-boot:run                                          # профиль dev (из application.yml)
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod          # профиль prod: HIGH -> MEDIUM -> LOW
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,prod      # оба репозитория: выигрывает @Primary
```

Чтобы показать ошибку «убери @Primary»: удалите `@Primary` у `InMemoryTaskRepository` и запустите
с профилями `dev,prod` - получите `NoUniqueBeanDefinitionException`.

### Структура

| Класс | Назначение |
|---|---|
| `model/Task`, `Priority`, `Status` | модель и enum-ы |
| `repository/TaskRepository` | интерфейс хранилища |
| `repository/InMemoryTaskRepository` | `@Primary`, `@Profile("dev")`, хранение в `List` |
| `repository/PriorityTaskRepository` | `@Profile("prod")`, выдача по приоритету |
| `service/TaskService`, `TaskServiceImpl` | сервис, `@PostConstruct`, `@PreDestroy`, лимит `app.max-tasks` |
| `service/TaskStatsService` | `@Scope("prototype")`, свой UUID у каждого экземпляра |
| `config/AppConfig` | `@Configuration` + `@Bean` с `@Qualifier` |
| `config/AppProperties` | `@Value` из `application.yml` |
| `runner/AppRunner` | весь сценарий (шаги 1-8) |

## Часть 2. Spring Web MVC / REST

`TaskController` (`@RestController`, `/tasks`) поверх существующего сервиса; ошибки в JSON -
`GlobalExceptionHandler` (`@ControllerAdvice`).

| Метод | URI | Ответ |
|---|---|---|
| GET | `/tasks` | 200, список; фильтры `?status=DONE`, `?priority=HIGH`, `?status=NEW&priority=LOW` |
| GET | `/tasks/{id}` | 200 или 404 |
| POST | `/tasks` | 201 + `Location` |
| PUT | `/tasks/{id}` | 200 или 404 |
| PATCH | `/tasks/{id}/status` (`{"status":"DONE"}`) | 200 или 404 |
| DELETE | `/tasks/{id}` | 204 без тела |
| GET | `/tasks/stats` | 200, `{"NEW":3,"IN_PROGRESS":1,"DONE":2}` |

```bash
curl -i -X POST localhost:8080/tasks -H 'Content-Type: application/json' \
     -d '{"title":"Купить молоко","priority":"HIGH"}'          # 201 Created
curl -i localhost:8080/tasks/999                                # 404 + JSON {"status":404,"error":"Task not found",...}
curl -i -X POST localhost:8080/tasks -H 'Content-Type: application/json' -d '{"title":""}'   # 400
```

## Часть 3. Spring Data JPA

`InMemoryTaskRepository` заменён на `TaskRepository extends JpaRepository<Task, Long>` (H2, `create-drop`).
Контроллер для CRUD-методов не менялся - изменился только `GET /tasks` (пагинация, шаг 4.1 ТЗ) и появились
`/tasks/search` и `/tasks/today`.

* `Task` - `@Entity` (`@Enumerated(EnumType.STRING)`, конструктор без параметров, `createdAt`).
* Derived queries: `findByStatus`, `findByPriority`, `findByStatusAndPriority`, `findByTitleContainingIgnoreCase`, `countByStatus`.
* JPQL `@Query`: `countGroupedByStatus` (статистика), `searchByKeyword`, `findCreatedBetween` (созданные сегодня).
* Пагинация: `GET /tasks?page=0&size=3&sort=id` (или `sort=title,desc`), ответ - `content`, `totalElements`, `totalPages`...
* SQL видно в логе (`show-sql`), H2-консоль: <http://localhost:8080/h2-console> (JDBC URL `jdbc:h2:mem:taskdb`, user `sa`, пароль пустой).

```bash
curl 'localhost:8080/tasks?page=0&size=3&sort=id'
curl 'localhost:8080/tasks?status=NEW&priority=HIGH'
curl 'localhost:8080/tasks/search?keyword=купить'
curl  localhost:8080/tasks/stats
```

## Между частями 3 и 4. DTO + валидация

База Части 4 в ТЗ - «DTO + валидация сданы» (отдельного задания в документе нет, поэтому этот шаг оформлен
отдельным коммитом, тег `part-3b-dto-validation`). `@Entity` больше не отдаётся через API напрямую.

* `TaskRequest` (`@NotBlank`, `@Size`), `StatusUpdateRequest` (`@NotNull`), `TaskResponse` (priority/status - строки).
* `@Valid` в контроллере; `GlobalExceptionHandler` отдаёт 400 с `fieldErrors`.
* Сервис работает с DTO: `create`, `getAll`, `findById`, `getOrThrow`, `update`, `updateStatus`, `delete`.
* Работают оба префикса: `/tasks` и `/api/tasks` (в ТЗ разных частей используются оба).
* Значения enum в JSON регистронезависимы (`"priority":"high"`).

```bash
curl -i -X POST localhost:8080/api/tasks -H 'Content-Type: application/json' -d '{"title":"  "}'
# 400 {"status":400,"error":"Validation failed","message":"Название задачи не может быть пустым","fieldErrors":{"title":"..."}}
```

## Часть 4. AOP - логирование и тайминг

Весь код аспектов - в пакете `aspect/`. `TaskService`, `TaskController`, `TaskRepository` не содержат ни одной
строки про логирование или замер времени (главный критерий ТЗ).

| Класс | Что делает |
|---|---|
| `aspect/LoggingAspect` | `@Pointcut taskServiceMethods()` + `@Before` (имя метода и аргументы), `@After`, `@Around` (замер времени, `pjp.proceed()`, `throw e`), `@AfterThrowing` (класс, метод, сообщение; исключение не подавляется) |
| `annotation/Loggable` | `@Retention(RUNTIME)`, `@Target(METHOD)` |
| `aspect/AnnotationLoggingAspect` | срабатывает только на методах с `@Loggable` (в `TaskServiceImpl`: `create`, `update`, `delete`) |

Пример лога для `POST /tasks`:

```
→ create called with args: [TaskRequest{title='Купить молоко', priority=HIGH, status=null}]
[@Loggable] Создание задачи -> TaskService.create(..)
create completed in 12ms
[@Loggable] Создание задачи -> OK
← create finished
```

Для `GET /tasks/999` сработают `@Before`, `@AfterThrowing` (✖ ... threw TaskNotFoundException), `@After`, а клиент всё равно получит
404 JSON от `@ControllerAdvice`.

Ограничение прокси: вызов метода своего же класса (`getOrThrow -> findById`) идёт мимо прокси, поэтому аспект его не видит.

## Часть 5. @Transactional

* `TaskServiceImpl` помечен `@Transactional` (read-write по умолчанию); методы чтения (`getAll`, `findById`, `getOrThrow`,
  `countByStatus`, `search`, `getCreatedToday`) - `@Transactional(readOnly = true)`.
* Профиль `dev` включает логи транзакций: `Creating new transaction ... / Committing / Rolling back`
  (`DELETE /api/tasks/999` - в логе `Rolling back JPA transaction`).
* `AuditLog` (`audit_logs`: id, action, taskId, createdAt), `AuditLogRepository`, `AuditService.logAction()` с
  `@Transactional(propagation = REQUIRES_NEW)`; вызывается из `create()` (`CREATE`) и `delete()` (`DELETE`).
  `AuditService` - отдельный бин, поэтому вызов идёт через прокси (с `this.` транзакция бы не открылась).
* Демонстрации из ТЗ (checked exception без/с `rollbackFor`, аудит переживает откат) вынесены в тесты, а не в
  production-код: `TransactionDemoService` (`@TestComponent`) + `TransactionBehaviorTest`.
* `TaskRepositoryTest` - `@DataJpaTest` с настоящей H2.

| Ситуация | Результат |
|---|---|
| `RuntimeException` в `@Transactional`-методе | откат (по умолчанию) |
| checked `IOException` без `rollbackFor` | **коммит** - данные остаются |
| checked `IOException` + `rollbackFor = Exception.class` | откат |
| `REQUIRES_NEW` (аудит) при откате основной транзакции | запись аудита сохраняется |

## Часть 6. Миграции БД: Flyway и Liquibase

`ddl-auto=create-drop` заменён на `ddl-auto=none` + Flyway. Схема - в SQL-миграциях:

| Файл | Что делает |
|---|---|
| `db/migration/V1__create_tasks_table.sql` | таблица `tasks` |
| `db/migration/V2__add_created_at_to_tasks.sql` | `created_at TIMESTAMP DEFAULT NOW()` (у старых строк заполняется сразу) |
| `db/migration/V3__create_audit_logs_table.sql` | `audit_logs` (сущность из Части 5) |
| `db/seed/R__seed_demo_tasks.sql` | демо-данные, **только профиль dev** (repeatable-миграция) |

* Профиль `dev` использует файловую H2 (`./data/taskdb`), поэтому данные **переживают перезапуск** и видно, что V1 повторно не применяется
  (`flyway_schema_history`). Демо: удалите `./data` - миграции создадут схему и демо-данные заново.
  Измените уже применённый `V1__...sql` - Flyway откажется стартовать (checksum mismatch); исправление - только новой миграцией `V4`.
* Демо-данные вынесены из `V1`: схема не должна тащить за собой тестовые строки (в тестах и в docker их нет).
  Тесты идут с профилем `test` (`application-test.yml`): схема из тех же миграций, но без seed.
* **Liquibase** - те же изменения в SQL-формате с `--rollback`: `db/changelog/db.changelog-master.xml` + `changes/001..003-*.sql`.
  Запуск: `./mvnw spring-boot:run -Dspring-boot.run.profiles=liquibase` (Flyway при этом выключен; история - таблица `DATABASECHANGELOG`).
* `FlywayMigrationTest` проверяет порядок применённых миграций и колонки таблиц.

### Сравнение Flyway и Liquibase

| Критерий | Flyway | Liquibase |
|---|---|---|
| Формат миграций | SQL-файлы `V<версия>__<имя>.sql` | XML / YAML / JSON / SQL (`--liquibase formatted sql`) |
| Таблица истории | `flyway_schema_history` | `DATABASECHANGELOG` (+ `DATABASECHANGELOGLOCK`) |
| Откат | в Community-версии нет (только платно) | `--rollback` у каждого changeSet |
| Порог входа | низкий: пишешь обычный SQL | средний: свой формат, changeSet-ы, master-файл |
| Абстракция от БД | нет, SQL конкретной СУБД | есть (в XML/YAML), в SQL-формате - нет |
| **Мой выбор** | **Flyway** | |

**Выбор для Task Manager - Flyway:** проект небольшой, схема одна (H2 для разработки, PostgreSQL в Docker), SQL в обоих случаях
почти одинаковый; обычные SQL-файлы проще читать и ревьюить, а откат в учебном проекте делается новой миграцией вперёд.
Liquibase стоило бы выбрать, если нужны бесплатный rollback или одна и та же схема на сильно разных СУБД без правки SQL.

## Часть 7. Юнит-тесты: JUnit 5 + Mockito

`src/test/java/com/example/service/TaskServiceTest.java` - тесты `TaskServiceImpl` **без** Spring-контекста
(`@ExtendWith(MockitoExtension.class)`, нет `@SpringBootTest`): `@Mock TaskRepository`, `@Mock AuditService`,
`@Spy AppProperties`, `@InjectMocks TaskServiceImpl` (constructor injection). Каждый тест - Arrange / Act / Assert.

| Метод | Тесты |
|---|---|
| `getAll()` | список DTO; пустой список; фильтры `status`+`priority` (derived query); страница и сортировка (`ArgumentCaptor`); неизвестный статус, неверный `size`/`sort` -> `IllegalArgumentException` |
| `create()` | статус `NEW`; вызов `save`; запись аудита `CREATE`; приоритет по умолчанию; пустой title -> репозиторий не вызывается (`verifyNoInteractions`); лимит -> `TaskLimitExceededException` |
| `findById()` / `getOrThrow()` | найден; не найден -> `Optional.empty()`; не найден -> `TaskNotFoundException` с id в сообщении |
| `update()` / `updateStatus()` | обновлённые поля; статус не меняется, если не передан; задачи нет -> пусто и `save` не вызывается; меняется только статус |
| `delete()` | `deleteById` + аудит `DELETE`; нет задачи -> исключение, `deleteById` не вызывается |
| остальное | статистика по статусам (с нулями), поиск, «созданные сегодня», prototype `getStats()` |

```bash
./mvnw test                       # все тесты
./mvnw test -Dtest=TaskServiceTest
```

## Части 8 и 9. Spring Security + JWT

В документе два ТЗ на один и тот же шаг («JWT + Authentication» и «Spring Security + JWT»): они отличаются префиксом путей
(`/api/...` и `/...`), форматом ролей, сроками токенов и кодами ошибок. Реализован **объединённый вариант**, который
выполняет оба; на один коммит поставлены два тега: `part-8-security-jwt` и `part-9-security-jwt`.

### Как это работает

```
POST /auth/register|login -> AuthService -> JwtService.generateToken -> {"token","accessToken","refreshToken"}
запрос с "Authorization: Bearer <accessToken>" -> JwtAuthFilter -> SecurityContext -> @PreAuthorize -> контроллер
```

| Компонент | Что делает |
|---|---|
| `model/User` (`users`) | email (unique), BCrypt-хеш, `Role` (`ROLE_USER`, `ROLE_ADMIN`); реализует `UserDetails` |
| `security/JwtService` | JJWT 0.12: `generateToken`, `extractUsername`, `isTokenValid`; секрет - Base64, ≥ 256 бит |
| `security/JwtAuthFilter` | читает Bearer-токен; просроченный -> `401 {"error":"Token expired"}`; подделанный -> анонимный запрос -> 401 |
| `security/SecurityExceptionHandler` | JSON для 401 (`AuthenticationEntryPoint`) и 403 (`AccessDeniedHandler`) |
| `config/SecurityConfig` | stateless, CSRF выключен, `@EnableMethodSecurity`, публично только `/auth/**`, `/api/auth/**` и Swagger |
| `service/AuthService`, `RefreshTokenService` | регистрация (всегда USER), вход, refresh с **ротацией**, logout |
| `controller/AuthController` | `/auth/register`, `/login`, `/refresh`, `/logout` (то же под `/api/auth/...`) |
| `config/OpenApiConfig` | Swagger UI: <http://localhost:8080/swagger-ui.html>, кнопка Authorize (Bearer) |
| `runner/AdminInitializer` | тестовый ADMIN при старте (dev: `admin@example.com` / `admin12345`) |

Права на задачи (`TaskController` + `TaskServiceImpl`, текущий пользователь передаётся в сервис через `@AuthenticationPrincipal`):

* `Task.owner` (`tasks.owner_id`); создавая задачу, пользователь становится владельцем.
* `GET /tasks`, `/stats`, `/search`, `/today`: обычный пользователь видит только свои задачи, ADMIN - все. `GET /tasks/my` - только свои (и у ADMIN).
* `POST` - `hasAnyRole('USER','ADMIN')`; `PUT`, `PATCH /{id}/status`, `DELETE` -
  `hasRole('ADMIN') or @taskService.isOwner(#id, authentication.name)`; чужая задача -> **403**.
  Дополнительно сервис сам бросает `AccessDeniedException("Not your task")`.

### Где два ТЗ расходились и что выбрано

| Вопрос | Часть 8 | Часть 9 | В проекте |
|---|---|---|---|
| Префикс | `/api/auth`, `/api/tasks` | `/auth`, `/tasks` | **работают оба** |
| Роли | `USER`/`ADMIN` | `ROLE_USER`/`ROLE_ADMIN` | `ROLE_USER`/`ROLE_ADMIN` (`hasRole('ADMIN')` работает) |
| Ответ login | `token`, затем `accessToken`+`refreshToken` | `token`, затем `accessToken`+`refreshToken` | все три поля |
| Дубликат email | 400 | 409 | **409** `{"error":"Email already registered"}` |
| Просроченный/неизвестный refresh | 403 | 401 | **401** |
| Срок access-токена | 15 минут | 24 часа | 24 часа (`JWT_EXPIRATION=900000` - 15 минут) |
| Удаление | только ADMIN (п. 3) / владелец или ADMIN (п. 5) | только ADMIN / свои задачи | **владелец или ADMIN** |
| `getAll` | только свои | все + `/my` | свои (ADMIN - все) + `/my` |

> **Ошибка в ТЗ Части 9:** секрет `dGVzdFNlY3JldEtleVRoYXRJc0xvbmdFbm91Z2g=` после декодирования - всего 29 байт (232 бита),
> JJWT выбросит `WeakKeyException`. В проекте используется секрет ≥ 48 байт, а `JwtServiceTest` показывает, что
> короткий секрет отвергается.

### Быстрая проверка

```bash
curl -s -X POST localhost:8080/auth/register -H 'Content-Type: application/json' \
     -d '{"email":"user@test.com","password":"password123"}'          # {"token":"eyJ...","accessToken":"...","refreshToken":"..."}
TOKEN=<accessToken>
curl -s localhost:8080/tasks -H "Authorization: Bearer $TOKEN"        # 200, без токена - 401
curl -i -X DELETE localhost:8080/tasks/1 -H "Authorization: Bearer $TOKEN"   # чужая задача - 403
```

### Тесты

| Тест | Что проверяет |
|---|---|
| `JwtServiceTest` | генерация/проверка токена, истёкший, подделанный, чужой секрет, слабый секрет (`WeakKeyException`) |
| `AuthServiceTest` | BCrypt, роль USER, нормализация email, ротация refresh-токена, logout (Mockito) |
| `TaskServiceTest` | владелец, `AccessDeniedException("Not your task")`, видимость задач USER/ADMIN, `isOwner` |
| `TaskControllerTest` (`@WebMvcTest`) | 401/403/404/400/201/204, SpEL `@taskService.isOwner`, `JwtAuthFilter` (истёк, битый, валидный токен) |
| `AuthControllerTest` (`@WebMvcTest`) | register 200/409/400, login 200/401, refresh 200/401, logout 401/204 |
| `AuthFlowIntegrationTest` (`@SpringBootTest`) | register -> login -> задачи; USER не удалит чужую задачу, ADMIN удалит; `/tasks/my`; ротация refresh; logout; Swagger |

> **401 и 403:** 401 - «не знаю, кто вы» (нет или неверный токен), 403 - «знаю, но не пущу» (нет прав).
> `@ControllerAdvice` их не перехватывает: они возникают в цепочке фильтров раньше `DispatcherServlet`
> (поэтому нужны `AuthenticationEntryPoint` и `AccessDeniedHandler`).
