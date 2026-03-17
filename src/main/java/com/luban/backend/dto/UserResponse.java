package com.luban.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

/**
 * User in API responses (no password). Field names match API doc (camelCase).
 */
public record UserResponse(
    String id,
    String username,
    String name,
    String role,
    String status,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt
) {
    public static UserResponse fromEntity(com.luban.backend.entity.User u) {
        if (u == null) return null;
        return new UserResponse(
            u.getId(),
            u.getUsername(),
            u.getName(),
            u.getRole(),
            u.getStatus(),
            u.getCreatedAt(),
            u.getUpdatedAt()
        );
    }
}
