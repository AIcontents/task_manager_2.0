package com.example.exception;

import com.example.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Один класс - обработка исключений всех REST-контроллеров: вместо stacktrace и 500
 * клиент получает понятный JSON и правильный HTTP-статус.
 * <p>
 * Ограничение annotations = RestController.class нужно, чтобы позже этот JSON-обработчик
 * не перехватывал ошибки HTML-контроллеров (Thymeleaf).
 */
@ControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(TaskNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "Task not found", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage());
    }

    @ExceptionHandler(TaskLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleLimitExceeded(TaskLimitExceededException ex) {
        return build(HttpStatus.CONFLICT, "Task limit exceeded", ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request body",
                "Тело запроса отсутствует, содержит некорректный JSON или недопустимое значение поля "
                        + "(priority: LOW, MEDIUM, HIGH; status: NEW, IN_PROGRESS, DONE)");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "Bad request",
                "Параметр '" + ex.getName() + "' имеет неверное значение: '" + ex.getValue() + "'");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), error, message));
    }
}
