package com.example.dto;

import com.example.model.Priority;
import com.example.model.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Входной DTO для создания и обновления задачи: @Entity наружу не торчит,
 * а проверка данных описана аннотациями (@Valid в контроллере).
 * Обычный класс с геттерами/сеттерами: его используют и REST (@RequestBody), и HTML-формы (@ModelAttribute).
 */
public class TaskRequest {

    @NotBlank(message = "Название задачи не может быть пустым")
    @Size(max = 255, message = "Название задачи не должно быть длиннее 255 символов")
    private String title;

    @Size(max = 1000, message = "Описание не должно быть длиннее 1000 символов")
    private String description;

    /** Необязателен: если не указан, берётся app.default-priority. */
    private Priority priority;

    /** Необязателен: используется при обновлении (PUT); при создании всегда NEW. */
    private Status status;

    public TaskRequest() {
    }

    public TaskRequest(String title, String description, Priority priority) {
        this.title = title;
        this.description = description;
        this.priority = priority;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "TaskRequest{title='" + title + '\''
                + ", priority=" + priority
                + ", status=" + status + '}';
    }
}
