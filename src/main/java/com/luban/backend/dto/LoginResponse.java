package com.luban.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * POST /backend/auth/login response: user + claims.
 */
public record LoginResponse(UserResponse user, Map<String, String> claims) {

    public static LoginResponse of(UserResponse user) {
        return new LoginResponse(user, Map.of(
            "userId", user.id(),
            "role", user.role()
        ));
    }
}
