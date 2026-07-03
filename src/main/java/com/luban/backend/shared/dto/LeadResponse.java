package com.luban.backend.shared.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * 线索响应。contactMasked 为脱敏后的联系人字段（phone→138****1234, email→a***@x.com）。
 */
public record LeadResponse(
        String id,
        String siteId,
        String formId,
        String pageId,
        String channelId,
        Map<String, String> contactMasked,
        Map<String, String> utm,
        String status,
        String assigneeId,
        String sourceIp,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant convertedAt,
        @JsonProperty("formName") String formName
) {}
