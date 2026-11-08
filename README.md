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
