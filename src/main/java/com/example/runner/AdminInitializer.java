package com.example.runner;

import com.example.model.Role;
import com.example.model.User;
import com.example.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Создаёт тестового ADMIN при старте. Работает только если заданы app.admin.email и app.admin.password
 * (в dev - application.yml, в docker - переменные APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD; в тестах не создаётся).
 * Регистрация через API всегда создаёт роль USER, поэтому ADMIN может появиться только так.
 */
@Component
@ConditionalOnProperty(prefix = "app.admin", name = {"email", "password"})
public class AdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminInitializer(UserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            @Value("${app.admin.email}") String email,
                            @Value("${app.admin.password}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            return;
        }
        userRepository.save(new User(normalizedEmail, passwordEncoder.encode(password), Role.ROLE_ADMIN));
        log.info("Создан администратор: {}", normalizedEmail);
    }
}
