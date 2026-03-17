package com.luban.backend.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Unified error body for BFF; aligned with luban-backend-go handler.APIError.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record APIError(String code, String message, Object details) {

    public APIError(String code, String message) {
        this(code, message, null);
    }
}
