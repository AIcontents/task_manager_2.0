package com.example.repository;

import com.example.model.RefreshToken;
import com.example.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    /** Удаляет все refresh-токены пользователя (logout). Вызывать внутри транзакции. */
    void deleteByUser(User user);
}
