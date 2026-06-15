package com.luban.backend.dto;

/**
 * 线索状态流转请求。
 */
public record LeadStatusUpdateRequest(String status, String assigneeId) {}
