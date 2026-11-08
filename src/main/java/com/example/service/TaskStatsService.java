package com.example.service;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Бин со scope prototype: Spring создаёт НОВЫЙ экземпляр при каждом запросе бина,
 * поэтому у каждого экземпляра свой UUID. (У singleton-бина UUID был бы один на всё приложение.)
 */
@Component
@Scope("prototype")
public class TaskStatsService {

    private final UUID id = UUID.randomUUID();
    private final Instant createdAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
