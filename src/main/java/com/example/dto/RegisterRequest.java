package com.example.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный формат email")
    @Size(max = 255, message = "Email не должен быть длиннее 255 символов")
    private String email;

    // BCrypt учитывает только первые 72 байта пароля - длиннее принимать нет смысла
    @NotBlank(message = "Пароль обязателен")
    @Size(min = 8, max = 72, message = "Пароль должен содержать от 8 до 72 символов")
    private String password;

    public RegisterRequest() {
    }

    public RegisterRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /** Пароль в toString не выводим. */
    @Override
    public String toString() {
        return "RegisterRequest{email='" + email + "'}";
    }
}
