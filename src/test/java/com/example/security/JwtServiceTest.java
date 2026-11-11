package com.example.security;

import com.example.model.Role;
import com.example.model.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Юнит-тесты JwtService без Spring: сервис создаётся обычным конструктором. */
class JwtServiceTest {

    // Тестовые секреты: Base64 от 48 случайных байт (384 бита; минимум для HS256 - 256 бит)
    private static final String SECRET = "93/RdVC5LXvRIfJ4rpu+rSRuh3WwbE/u/uT0KJPRnxzeHhjLu8fOds0UPr0Aj93Y";
    private static final String OTHER_SECRET = "Ezs729vzqoCpF6mmZLi++yAV0swaM9oLoVw9wSPZa7zT98g1wu2YZSrA9I7JZzVW";
    // Секрет из ТЗ Части 9 - после декодирования это всего 29 байт (232 бита), для HMAC-SHA он слишком короткий
    private static final String SECRET_FROM_SPEC_PART_9 = "dGVzdFNlY3JldEtleVRoYXRJc0xvbmdFbm91Z2g=";

    private static final long ONE_HOUR_MS = 3_600_000L;

    private final User alice = new User("alice@test.com", "secret-hash", Role.ROLE_USER);
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, ONE_HOUR_MS);
    }

    @Test
    void generateToken_shouldContainCorrectUsername() {
        String token = jwtService.generateToken(alice);

        assertThat(token.split("\\.")).hasSize(3); // header.payload.signature
        assertThat(jwtService.extractUsername(token)).isEqualTo("alice@test.com");
    }

    @Test
    void generateToken_shouldNotContainPasswordInReadablePayload() {
        String token = jwtService.generateToken(alice);

        // payload - просто Base64, читается любым; поэтому секреты в токен класть нельзя
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(payload).contains("\"sub\":\"alice@test.com\"");
        assertThat(payload).doesNotContain("secret-hash").doesNotContain("password");
    }

    @Test
    void isTokenValid_withValidToken_shouldReturnTrue() {
        String token = jwtService.generateToken(alice);

        assertThat(jwtService.isTokenValid(token, alice)).isTrue();
    }

    @Test
    void isTokenValid_forAnotherUser_shouldReturnFalse() {
        String token = jwtService.generateToken(alice);
        User bob = new User("bob@test.com", "hash", Role.ROLE_USER);

        assertThat(jwtService.isTokenValid(token, bob)).isFalse();
    }

    @Test
    void isTokenValid_withExpiredToken_shouldReturnFalse() {
        JwtService shortLived = new JwtService(SECRET, -1_000L); // срок действия уже в прошлом
        String expiredToken = shortLived.generateToken(alice);

        assertThat(shortLived.isTokenValid(expiredToken, alice)).isFalse();
    }

    @Test
    void extractUsername_withExpiredToken_shouldThrowExpiredJwtException() {
        JwtService shortLived = new JwtService(SECRET, -1_000L);
        String expiredToken = shortLived.generateToken(alice);

        // именно это исключение JwtAuthFilter превращает в 401 {"error":"Token expired"}
        assertThatThrownBy(() -> shortLived.extractUsername(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void extractUsername_withTamperedSignature_shouldThrowJwtException() {
        String token = jwtService.generateToken(alice);
        String[] parts = token.split("\\.");
        // меняем символ в СЕРЕДИНЕ подписи (последний символ Base64 может нести неиспользуемые биты)
        char original = parts[2].charAt(5);
        char replacement = original == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "."
                + parts[2].substring(0, 5) + replacement + parts[2].substring(6);

        assertThatThrownBy(() -> jwtService.extractUsername(tampered)).isInstanceOf(JwtException.class);
        assertThat(jwtService.isTokenValid(tampered, alice)).isFalse();
    }

    @Test
    void isTokenValid_withTokenSignedByDifferentSecret_shouldReturnFalse() {
        String foreignToken = new JwtService(OTHER_SECRET, ONE_HOUR_MS).generateToken(alice);

        assertThat(jwtService.isTokenValid(foreignToken, alice)).isFalse();
    }

    @Test
    void isTokenValid_withGarbage_shouldReturnFalse() {
        assertThat(jwtService.isTokenValid("not-a-jwt", alice)).isFalse();
    }

    @Test
    void constructor_withTooShortSecret_shouldThrowWeakKeyException() {
        // секрет короче 256 бит: приложение не должно стартовать с таким ключом
        assertThatThrownBy(() -> new JwtService(SECRET_FROM_SPEC_PART_9, ONE_HOUR_MS))
                .isInstanceOf(WeakKeyException.class);
    }
}
