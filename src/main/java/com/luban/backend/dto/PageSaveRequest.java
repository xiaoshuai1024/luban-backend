package com.luban.backend.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record PageSaveRequest(
    @NotBlank String name,
    @NotBlank String path,
    String status,
    JsonNode schema
) {}
