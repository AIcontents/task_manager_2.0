package com.example.service;

import com.example.dto.AuthResponse;
import com.example.dto.LoginRequest;
import com.example.dto.RegisterRequest;
import com.example.exception.EmailAlreadyRegisteredException;
import com.example.exception.InvalidRefreshTokenException;
import com.example.model.RefreshToken;
import com.example.model.Role;
import com.example.model.User;
import com.example.repository.UserRepository;
import com.example.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Юнит-тесты AuthService (регистрация, вход, ротация refresh-токенов, logout) без Spring-контекста. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    // ---------- register() ----------

    @Test
    void register_shouldNormalizeEmailEncodePasswordAndCreateUserWithRoleUser() {
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-hash");
        when(jwtService.generateToken(any(User.class))).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn(refreshToken("refresh-token", null));

        AuthResponse response = authService.register(new RegisterRequest("  New@Test.com ", "password123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("new@test.com");
        assertThat(saved.getPassword()).isEqualTo("encoded-hash").isNotEqualTo("password123");
        assertThat(saved.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(response.getToken()).isEqualTo("access-token");
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void register_withExistingEmail_shouldThrowAndNotSave() {
        when(userRepository.existsByEmail("taken@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("taken@test.com", "password123")))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessageContaining("taken@test.com");

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(jwtService, refreshTokenService);
    }

    @Test
    void register_whenUniqueConstraintViolatedInDatabase_shouldThrowEmailAlreadyRegistered() {
        when(userRepository.existsByEmail("race@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> authService.register(new RegisterRequest("race@test.com", "password123")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verifyNoInteractions(jwtService, refreshTokenService);
    }

    // ---------- login() ----------

    @Test
    void login_withValidCredentials_shouldReturnTokens() {
        User user = user(1L, "alice@test.com");
        Authentication authenticated = new UsernamePasswordAuthenticationToken("alice@test.com", null);
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticated);
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(user)).thenReturn(refreshToken("refresh-token", user));

        AuthResponse response = authService.login(new LoginRequest("Alice@Test.com", "password123"));

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void login_withWrongPassword_shouldPropagateBadCredentialsAndIssueNoTokens() {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@test.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(jwtService, refreshTokenService);
    }

    // ---------- refresh() ----------

    @Test
    void refresh_withValidToken_shouldRotateRefreshTokenAndIssueNewPair() {
        User user = user(1L, "alice@test.com");
        RefreshToken stored = refreshToken("old-refresh", user);
        when(refreshTokenService.findByToken("old-refresh")).thenReturn(Optional.of(stored));
        when(jwtService.generateToken(user)).thenReturn("new-access");
        when(refreshTokenService.createRefreshToken(user)).thenReturn(refreshToken("new-refresh", user));

        AuthResponse response = authService.refresh("old-refresh");

        verify(refreshTokenService).verifyExpiration(stored);
        verify(refreshTokenService).delete(stored); // ротация: использованный токен удалён
        assertThat(response.getAccessToken()).isEqualTo("new-access");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void refresh_withUnknownToken_shouldThrowInvalidRefreshToken() {
        when(refreshTokenService.findByToken("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("unknown"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verifyNoInteractions(jwtService);
    }

    @Test
    void refresh_withExpiredToken_shouldThrowAndIssueNothing() {
        RefreshToken expired = refreshToken("expired", user(1L, "alice@test.com"));
        expired.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(refreshTokenService.findByToken("expired")).thenReturn(Optional.of(expired));
        when(refreshTokenService.verifyExpiration(expired)).thenThrow(new InvalidRefreshTokenException("expired"));

        assertThatThrownBy(() -> authService.refresh("expired"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenService, never()).createRefreshToken(any(User.class));
        verifyNoInteractions(jwtService);
    }

    // ---------- logout() ----------

    @Test
    void logout_shouldDeleteRefreshTokensOfCurrentUser() {
        User user = user(1L, "alice@test.com");

        authService.logout(user);

        verify(refreshTokenService).deleteByUser(user);
    }

    // ---------- вспомогательные методы ----------

    private static User user(Long id, String email) {
        User user = new User(email, "hash", Role.ROLE_USER);
        user.setId(id);
        return user;
    }

    private static RefreshToken refreshToken(String value, User user) {
        RefreshToken token = new RefreshToken();
        token.setToken(value);
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusDays(7));
        return token;
    }
}
