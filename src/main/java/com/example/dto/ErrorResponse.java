package com.example.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** Единый формат JSON-ошибки REST API. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(int status, String error, String message, Map<String, String> fieldErrors) {

    public ErrorResponse(int status, String error, String message) {
        this(status, error, message, null);
    }
}
