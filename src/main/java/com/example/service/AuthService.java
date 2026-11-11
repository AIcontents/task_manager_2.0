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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    /** Регистрация всегда создаёт роль USER; пароль сохраняется только в виде BCrypt-хеша. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }
        User user = new User(email, passwordEncoder.encode(request.getPassword()), Role.ROLE_USER);
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // гонка: параллельный запрос успел зарегистрировать тот же email (сработал UNIQUE в БД)
            throw new EmailAlreadyRegisteredException(email);
        }
        return issueTokens(user);
    }

    /**
     * Проверка пароля - не наша забота: authenticate() сам сравнивает его с хешем из БД
     * (DaoAuthenticationProvider + UserDetailsService) и при неудаче бросает BadCredentialsException (401).
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalize(request.getEmail());
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Пользователь не найден"));
        return issueTokens(user);
    }

    /**
     * Обмен refresh-токена на новую пару токенов. Ротация: использованный refresh-токен удаляется,
     * выдаётся новый - украденный старый токен второй раз уже не сработает.
     * noRollbackFor: удаление просроченного токена (внутри verifyExpiration) должно сохраниться.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResponse refresh(String refreshTokenValue) {
        RefreshToken stored = refreshTokenService.findByToken(refreshTokenValue)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh-токен не найден или уже использован"));
        refreshTokenService.verifyExpiration(stored);
        User user = stored.getUser();
        refreshTokenService.delete(stored);
        return issueTokens(user);
    }

    /** Logout: удаляет refresh-токены пользователя, после этого /auth/refresh вернёт 401. */
    @Transactional
    public void logout(User user) {
        refreshTokenService.deleteByUser(user);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        return new AuthResponse(accessToken, refreshToken.getToken());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
