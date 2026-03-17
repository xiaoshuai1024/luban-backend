package com.luban.backend.dto;

import java.util.List;

public record UserListResponse(List<UserResponse> list, long total) {}
