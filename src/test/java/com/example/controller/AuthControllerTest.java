package com.example.controller;

import com.example.config.SecurityConfig;
import com.example.dto.AuthResponse;
import com.example.dto.LoginRequest;
import com.example.dto.RefreshTokenRequest;
import com.example.dto.RegisterRequest;
import com.example.exception.EmailAlreadyRegisteredException;
import com.example.exception.InvalidRefreshTokenException;
import com.example.model.Role;
import com.example.model.User;
import com.example.security.JwtService;
import com.example.security.SecurityExceptionHandler;
import com.example.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Веб-слой аутентификации: @WebMvcTest(AuthController) + замоканный AuthService. */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, SecurityExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    // ---------- register ----------

    @Test
    void register_withValidData_shouldReturn200AndTokens() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(new AuthResponse("access", "refresh"));

        // эндпоинт публичный: токен для регистрации не нужен
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest("new@test.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("access"))
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"));
    }

    @Test
    void register_shouldAlsoWorkUnderShortPrefix() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(new AuthResponse("access", "refresh"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest("new@test.com", "password123"))))
                .andExpect(status().isOk());
    }

    @Test
    void register_withExistingEmail_shouldReturn409() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new EmailAlreadyRegisteredException("taken@test.com"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest("taken@test.com", "password123"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Email already registered"));
    }

    @Test
    void register_withShortPassword_shouldReturn400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest("new@test.com", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        verifyNoInteractions(authService);
    }

    @Test
    void register_withInvalidEmail_shouldReturn400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest("not-an-email", "password123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());

        verifyNoInteractions(authService);
    }

    // ---------- login ----------

    @Test
    void login_withValidCredentials_shouldReturn200AndTokens() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(new AuthResponse("access", "refresh"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("alice@test.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("access"));
    }

    @Test
    void login_withWrongPassword_shouldReturn401() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("alice@test.com", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Неверный email или пароль"));
    }

    // ---------- refresh ----------

    @Test
    void refresh_withValidToken_shouldReturn200AndNewTokens() throws Exception {
        when(authService.refresh("old-refresh")).thenReturn(new AuthResponse("new-access", "new-refresh"));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshTokenRequest("old-refresh"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"));
    }

    @Test
    void refresh_withExpiredOrUnknownToken_shouldReturn401() throws Exception {
        when(authService.refresh("expired"))
                .thenThrow(new InvalidRefreshTokenException("Refresh-токен истёк. Выполните вход заново"));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshTokenRequest("expired"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid refresh token"));
    }

    @Test
    void refresh_withBlankToken_shouldReturn400() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.refreshToken").exists());
    }

    // ---------- logout (требует access-токен) ----------

    @Test
    void logout_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/auth/logout")).andExpect(status().isUnauthorized());

        verifyNoInteractions(authService);
    }

    @Test
    void logout_whenAuthenticated_shouldReturn204AndRevokeTokens() throws Exception {
        User alice = new User("alice@test.com", "hash", Role.ROLE_USER);
        alice.setId(1L);

        mockMvc.perform(post("/auth/logout").with(user(alice)))
                .andExpect(status().isNoContent());

        verify(authService).logout(alice);
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
