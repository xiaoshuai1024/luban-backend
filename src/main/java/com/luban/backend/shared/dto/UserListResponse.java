package com.luban.backend.shared.dto;

import java.util.List;

public record UserListResponse(List<UserResponse> list, long total) {}
