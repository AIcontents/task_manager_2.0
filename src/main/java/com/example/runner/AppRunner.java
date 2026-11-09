package com.example.runner;

import com.example.config.AppProperties;
import com.example.dto.TaskRequest;
import com.example.dto.TaskResponse;
import com.example.model.Priority;
import com.example.model.Status;
import com.example.repository.TaskRepository;
import com.example.service.TaskService;
import com.example.service.TaskStatsService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Весь сценарий запуска Части 1 (шаги 1-8 из ТЗ).
 * После добавления веб-слоя сценарий выключен по умолчанию (в ТЗ Части 2: «CommandLineRunner можно
 * закомментировать - при наличии веб-слоя он мешает»). Включается свойством app.runner.enabled=true.
 */
@Component
@ConditionalOnProperty(prefix = "app.runner", name = "enabled", havingValue = "true")
public class AppRunner implements CommandLineRunner {

    private final TaskService taskService;
    private final AppProperties appProperties;
    private final ApplicationContext applicationContext;

    public AppRunner(TaskService taskService,
                     AppProperties appProperties,
                     ApplicationContext applicationContext) {
        this.taskService = taskService;
        this.appProperties = appProperties;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(String... args) {
        step1Greeting();
        List<TaskResponse> created = step2AddTasks();
        step3EdgeCases();
        step4ChangeStatuses(created);
        step5PrototypeScope();
        step6ApplicationContext();
        step7Profile();
        step8Finish();
    }

    private void step1Greeting() {
        title("Шаг 1. Приветствие (@Value / AppProperties / @PostConstruct)");
        System.out.println("Добро пожаловать в " + appProperties.getName() + "! Лимит: "
                + appProperties.getMaxTasks() + ". Приоритет по умолчанию: "
                + appProperties.getDefaultPriority());
    }

    private List<TaskResponse> step2AddTasks() {
        title("Шаг 2. Добавление задач (@Component / constructor DI / TaskServiceImpl)");
        TaskResponse low = taskService.create(request("Полить цветы", "Раз в неделю", Priority.LOW));
        TaskResponse medium = taskService.create(request("Написать отчёт", "По лабораторной работе", Priority.MEDIUM));
        TaskResponse high = taskService.create(request("Сдать домашнее задание", "До пятницы", Priority.HIGH));
        taskService.getAll().forEach(task -> System.out.println("  " + task));
        return List.of(low, medium, high);
    }

    private void step3EdgeCases() {
        title("Шаг 3. Граничные случаи (@Value max-tasks / валидация в сервисе)");
        try {
            taskService.create(request("   ", "Пустое название", Priority.LOW));
            System.out.println("  Ожидалось исключение для пустого title, но его не было");
        } catch (IllegalArgumentException e) {
            System.out.println("  Пустой title -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        try {
            taskService.create(request("Лишняя задача", "Сверх лимита", Priority.LOW));
            System.out.println("  Лимит не достигнут (app.max-tasks=" + appProperties.getMaxTasks() + ")");
        } catch (RuntimeException e) {
            System.out.println("  Сверх лимита -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        System.out.println("  Приложение продолжает работу");
    }

    private void step4ChangeStatuses(List<TaskResponse> created) {
        title("Шаг 4. Изменение статусов (TaskRepository.findAll() + фильтрация)");
        taskService.updateStatus(created.get(0).getId(), Status.IN_PROGRESS);
        taskService.updateStatus(created.get(1).getId(), Status.DONE);
        System.out.println("  Только DONE:");
        taskService.getAll("DONE", null, 0, 100, "id").forEach(task -> System.out.println("    " + task));
        System.out.println("  Только HIGH:");
        taskService.getAll(null, "HIGH", 0, 100, "id").forEach(task -> System.out.println("    " + task));
    }

    private void step5PrototypeScope() {
        title("Шаг 5. Prototype и ObjectProvider");
        TaskStatsService first = taskService.getStats();
        TaskStatsService second = taskService.getStats();
        System.out.println("  getStats() #1 -> UUID " + first.getId());
        System.out.println("  getStats() #2 -> UUID " + second.getId());
        System.out.println("  UUID разные: " + !first.getId().equals(second.getId()));
    }

    private void step6ApplicationContext() {
        title("Шаг 6. ApplicationContext (getBean() + фильтрация имён бинов)");
        TaskRepository repository = applicationContext.getBean(TaskRepository.class);
        System.out.println("  getBean(TaskRepository.class) -> " + repository.getClass().getName());
        System.out.println("  Всего бинов в контексте: " + applicationContext.getBeanDefinitionCount());
        System.out.println("  Бины, в имени которых есть 'task':");
        Arrays.stream(applicationContext.getBeanDefinitionNames())
                .filter(name -> name.toLowerCase(Locale.ROOT).contains("task"))
                .sorted()
                .forEach(name -> System.out.println("    " + name));
    }

    private void step7Profile() {
        title("Шаг 7. Профили (@Profile)");
        System.out.println("  Активные профили: "
                + Arrays.toString(applicationContext.getEnvironment().getActiveProfiles()));
        System.out.println("  Задачи в хранилище:");
        taskService.getAll().forEach(task -> System.out.println("    " + task));
        System.out.println("  Примечание: в Части 1 профиль выбирал репозиторий (dev - InMemoryTaskRepository, "
                + "prod - PriorityTaskRepository: HIGH -> MEDIUM -> LOW). С Части 3 хранилище - Spring Data JPA; "
                + "реализации Части 1 см. тег part-1-spring-core.");
    }

    private void step8Finish() {
        title("Шаг 8. Завершение (@PreDestroy / жизненный цикл бина)");
        System.out.println("  Приложение останавливается: контекст закрывается, сработает @PreDestroy");
    }

    private static TaskRequest request(String title, String description, Priority priority) {
        return new TaskRequest(title, description, priority);
    }

    private void title(String text) {
        System.out.println();
        System.out.println("=== " + text + " ===");
    }
}
