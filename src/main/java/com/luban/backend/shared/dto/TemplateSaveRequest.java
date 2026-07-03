package com.luban.backend.shared.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Template 创建/更新请求 DTO（template-marketplace plan）。
 * schema 通过 schemaJson 字段传入（完整 PageSchema JSON 字符串）。
 */
public record TemplateSaveRequest(
        @NotBlank String slug,
        @NotBlank String name,
        @NotBlank String category,
        String description,
        String thumbnail,
        /** 完整 PageSchema JSON 字符串（创建/发布时必填） */
        String schemaJson,
        String changeNote
) {}
