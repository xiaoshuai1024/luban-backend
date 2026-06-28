package com.luban.backend.shared.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

/**
 * V2-T7 CollectionItem 保存请求。data 为内容数据 JSON。
 */
public record CollectionItemSaveRequest(
    @NotNull JsonNode data,
    String status
) {}
