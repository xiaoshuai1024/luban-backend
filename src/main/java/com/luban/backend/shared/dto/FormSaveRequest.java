package com.luban.backend.shared.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

/**
 * 表单创建/更新请求。
 */
public record FormSaveRequest(
        @NotBlank String siteId,
        String pageId,
        @NotBlank String name,
        JsonNode fieldSchema,
        JsonNode submitConfig,
        JsonNode dedupKeys,
        Integer dedupWindow,
        String dedupPolicy,
        JsonNode antiSpam,
        String status
) {}
