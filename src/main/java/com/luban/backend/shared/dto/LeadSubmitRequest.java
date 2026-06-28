package com.luban.backend.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * 公开留资提交请求（访客侧）。contact 为明文字段（服务端加密后入库）。
 */
public record LeadSubmitRequest(
        @NotBlank String formId,
        @NotNull Map<String, String> contact,
        String pageId,
        String channelId,
        Map<String, String> utm,
        String ip,
        String visitorId,
        String captchaToken
) {}
