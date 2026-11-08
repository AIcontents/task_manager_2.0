package com.example.config;

import com.example.model.Priority;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Настройки приложения из application.yml (app.name, app.max-tasks, app.default-priority).
 * Значения читаются через @Value, зависимости - только через конструктор.
 */
@Component
public class AppProperties {

    private final String name;
    private final int maxTasks;
    private final Priority defaultPriority;

    public AppProperties(@Value("${app.name}") String name,
                         @Value("${app.max-tasks}") int maxTasks,
                         @Value("${app.default-priority}") String defaultPriority) {
        this.name = name;
        this.maxTasks = maxTasks;
        // Неверное значение (например, URGENT) приведёт к ошибке на старте - fail fast
        this.defaultPriority = Priority.valueOf(defaultPriority.trim().toUpperCase());
    }

    @PostConstruct
    public void printSettings() {
        System.out.println("AppProperties загружены: app.name=" + name
                + ", app.max-tasks=" + maxTasks
                + ", app.default-priority=" + defaultPriority);
    }

    public String getName() {
        return name;
    }

    public int getMaxTasks() {
        return maxTasks;
    }

    public Priority getDefaultPriority() {
        return defaultPriority;
    }
}
