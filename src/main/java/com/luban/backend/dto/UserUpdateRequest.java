package com.luban.backend.dto;

public record UserUpdateRequest(String username, String name, String role, String status, String password) {}
