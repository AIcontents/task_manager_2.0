package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        // Теперь это веб-приложение: Tomcat держит процесс живым, а shutdown hook
        // (Ctrl+C) корректно закрывает контекст и вызывает @PreDestroy.
        SpringApplication.run(Application.class, args);
    }
}
