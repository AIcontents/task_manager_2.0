package com.example.model;

/**
 * Роли пользователей. Имена уже содержат префикс ROLE_, поэтому authority = role.name(),
 * а в SpEL работает hasRole('ADMIN') (Spring Security сам добавляет префикс ROLE_ при проверке).
 */
public enum Role {
    ROLE_USER,
    ROLE_ADMIN
}
