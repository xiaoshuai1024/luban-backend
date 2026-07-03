package com.luban.backend.shared.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 线索状态流转请求。
 */
public record LeadStatusUpdateRequest(@NotBlank String status, String assigneeId) {}
