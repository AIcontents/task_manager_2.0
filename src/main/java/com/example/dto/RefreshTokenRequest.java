package com.example.dto;

import jakarta.validation.constraints.NotBlank;

/** Тело запроса POST /auth/refresh: {"refreshToken":"..."}. */
public class RefreshTokenRequest {

    @NotBlank(message = "Поле refreshToken обязательно")
    private String refreshToken;

    public RefreshTokenRequest() {
    }

    public RefreshTokenRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
