package com.luban.backend.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 表单创建/更新请求。
 */
public record FormSaveRequest(
        String siteId,
        String pageId,
        String name,
        JsonNode fieldSchema,
        JsonNode submitConfig,
        JsonNode dedupKeys,
        Integer dedupWindow,
        String dedupPolicy,
        JsonNode antiSpam,
        String status
) {}
