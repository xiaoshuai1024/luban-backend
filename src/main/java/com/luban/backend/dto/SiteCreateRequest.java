package com.luban.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record SiteCreateRequest(
    @NotBlank String name,
    @NotBlank String slug,
    String baseUrl,
    String status
) {}
