package com.luban.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record UserStatusRequest(@NotBlank String status) {}
