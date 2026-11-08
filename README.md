# Task Manager (TaskAPI)

Сквозной учебный проект на Spring Boot 3: от Spring Core до Docker.
Каждая часть технического задания зафиксирована отдельным коммитом и тегом `part-N-...`
(`git tag -l`, `git checkout part-3-spring-data-jpa`).

## Часть 1. Spring Core

Консольное приложение (`CommandLineRunner`), хранение в памяти.
Концепции: IoC/DI, `@Component`, `@Configuration`/`@Bean`, `@Qualifier`, `@Primary`, `@Profile`,
`@Scope("prototype")`, `ObjectProvider`, `@Value`, `@PostConstruct`/`@PreDestroy`, `ApplicationContext`.

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
