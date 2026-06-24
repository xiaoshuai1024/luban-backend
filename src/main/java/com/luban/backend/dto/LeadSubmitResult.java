package com.luban.backend.dto;

/**
 * 留资提交结果。
 *
 * @param leadId 线索 ID
 * @param status 入库状态：new（接受）/ invalid（标记重复）
 * @param dedup  是否命中去重
 */
public record LeadSubmitResult(String leadId, String status, boolean dedup) {}
