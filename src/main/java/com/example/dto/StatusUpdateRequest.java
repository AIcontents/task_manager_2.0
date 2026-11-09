package com.example.dto;

import com.example.model.Status;
import jakarta.validation.constraints.NotNull;

/** Тело запроса PATCH /tasks/{id}/status: {"status":"DONE"}. */
public class StatusUpdateRequest {

    @NotNull(message = "Поле status обязательно (NEW, IN_PROGRESS или DONE)")
    private Status status;

    public StatusUpdateRequest() {
    }

    public StatusUpdateRequest(Status status) {
        this.status = status;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
