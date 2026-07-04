package com.luban.backend.shared.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

/**
 * 表单创建/更新请求。
 *
 * <p>表单业务上必须属于一个页面（forms.page_id NOT NULL + FK→pages），
 * 故 pageId 与 siteId 同等必填。早期漏加 @NotBlank 导致缺 pageId 时穿透到 DB
 * 才报 500（SQLIntegrityConstraintViolationException: Column 'page_id' cannot be null）。
 */
public record FormSaveRequest(
        @NotBlank String siteId,
        @NotBlank String pageId,
        @NotBlank String name,
        JsonNode fieldSchema,
        JsonNode submitConfig,
        JsonNode dedupKeys,
        Integer dedupWindow,
        String dedupPolicy,
        JsonNode antiSpam,
        String status
) {}
