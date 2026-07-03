package com.luban.backend.shared.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

/**
 * V2-T7 Collection 保存请求。
 * fieldSchema 为字段定义 JSON（如 {fields:[{name,title,type}]}）。
 */
public record CollectionSaveRequest(
    @NotBlank String name,
    JsonNode fieldSchema,
    String status
) {}
