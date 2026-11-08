package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(Application.class, args);

        // Подсказка из ТЗ: без shutdown hook @PreDestroy не вызывается при остановке JVM.
        // Spring Boot регистрирует его сам, но здесь это сделано явно (повторный вызов безвреден).
        context.registerShutdownHook();

        // Шаг 8 сценария: консольное приложение завершается, контекст закрывается,
        // срабатывает @PreDestroy у TaskServiceImpl.
        context.close();
    }
}
