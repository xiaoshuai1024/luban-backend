package com.luban.backend.shared.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 模板安装请求 DTO（template-marketplace plan）。
 * 把模板的 schema 拷贝为目标 site 下的一个新 draft Page。
 */
public record TemplateInstallRequest(
        @NotBlank String siteId,
        /** 目标 path（不传则自动生成 path-/templates/{slug}） */
        String path,
        /** 安装的版本号（不传则取最新版） */
        Integer version
) {}
