package com.example.exception;

/** Пользователь с таким email уже зарегистрирован (HTTP 409). */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String email) {
        super("Пользователь с email " + email + " уже зарегистрирован");
    }
}
