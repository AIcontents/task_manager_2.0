package com.example.dto;

/**
 * Ответ register / login / refresh.
 * <ul>
 *   <li>{@code accessToken} - короткоживущий JWT для заголовка Authorization: Bearer ...</li>
 *   <li>{@code refreshToken} - долгоживущий токен для получения нового accessToken</li>
 *   <li>{@code token} - то же значение, что и accessToken (поле из ТЗ Частей 8-9, на него рассчитаны Postman-скрипты)</li>
 * </ul>
 */
public class AuthResponse {

    private final String token;
    private final String accessToken;
    private final String refreshToken;

    public AuthResponse(String accessToken, String refreshToken) {
        this.token = accessToken;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public String getToken() {
        return token;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}
