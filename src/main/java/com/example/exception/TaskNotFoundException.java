package com.example.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Задача с указанным id не найдена. Для REST-контроллеров JSON с 404 формирует GlobalExceptionHandler,
 * а @ResponseStatus обеспечивает 404 (страница error/404) для HTML-контроллеров веб-интерфейса.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(Long id) {
        super("Задача с id=" + id + " не найдена");
    }
}
