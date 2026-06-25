package com.luban.backend.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Create/update payload for a datasource.
 *
 * <p>Aligned with luban-backend-go handler.datasourceSaveRequest.
 * {@code type} is validated against {@code static|api} in the service layer
 * (400→INVALID_ARGUMENT), not via bean-validation, so the contract error code is stable
 * across the two backends.
 */
public record DatasourceSaveRequest(
    @NotBlank String siteId,
    @NotBlank String name,
    @NotBlank String type,
    @NotNull JsonNode config
) {}
