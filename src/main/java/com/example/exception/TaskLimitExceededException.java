package com.example.exception;

/** Превышен лимит задач (app.max-tasks). */
public class TaskLimitExceededException extends RuntimeException {

    public TaskLimitExceededException(int maxTasks) {
        super("Достигнут лимит задач: app.max-tasks=" + maxTasks
                + ". Удалите существующие задачи или увеличьте лимит в application.yml");
    }
}
