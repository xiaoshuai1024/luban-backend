package com.luban.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record UserCreateRequest(
    @NotBlank String username,
    @NotBlank String password,
    String name,
    String role
) {}
