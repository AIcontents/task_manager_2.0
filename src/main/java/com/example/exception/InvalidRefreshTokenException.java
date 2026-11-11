package com.example.exception;

/** Refresh-токен не найден (или уже использован / отозван) либо истёк (HTTP 401). */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
