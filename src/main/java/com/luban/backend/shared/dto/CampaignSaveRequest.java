package com.luban.backend.shared.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/**
 * Campaign 创建/更新请求（app-deeplink-backend-arch plan T13）。
 */
public record CampaignSaveRequest(
        @NotBlank String siteId,
        @NotBlank String name,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant startAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant endAt,
        /** 状态流转（planned→active→completed/cancelled），更新时用 */
        String status
) {}
