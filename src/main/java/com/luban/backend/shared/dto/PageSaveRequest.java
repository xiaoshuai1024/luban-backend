package com.luban.backend.shared.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

/**
 * V2-T2: seo 可选字段（页面级 SEO，JsonNode 直存）。
 */
public record PageSaveRequest(
    @NotBlank String name,
    @NotBlank String path,
    String status,
    JsonNode schema,
    JsonNode seo
) {}
