package com.luban.backend.service;

import com.luban.backend.dto.UserListResponse;
import com.luban.backend.dto.UserResponse;
import com.luban.backend.entity.User;
import com.luban.backend.exception.BusinessException;
import com.luban.backend.mapper.UserMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public UserListResponse list(int page, int size, String keyword) {
        if (page <= 0) page = 1;
        if (size <= 0) size = 10;
        int offset = (page - 1) * size;
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        List<User> users = userMapper.list(kw, offset, size);
        long total = userMapper.count(kw);
        List<UserResponse> list = users.stream().map(UserResponse::fromEntity).collect(Collectors.toList());
        return new UserListResponse(list, total);
    }

    public UserResponse get(String id) {
        User u = userMapper.getById(id);
        if (u == null) throw BusinessException.userNotFound();
        return UserResponse.fromEntity(u);
    }

    public UserResponse create(String username, String password, String name, String role) {
        if (role == null || role.isBlank()) role = "user";
        if (password == null || password.isBlank()) throw BusinessException.invalidArgument("password required");
        String hash = passwordEncoder.encode(password);
        User u = new User();
        u.setId(UUID.randomUUID().toString());
        u.setUsername(username);
        u.setName(name != null ? name : "");
        u.setRole(role);
        u.setStatus("active");
        u.setPassword(hash);
        Instant now = Instant.now();
        u.setCreatedAt(now);
        u.setUpdatedAt(now);
        try {
            userMapper.insert(u);
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                throw BusinessException.usernameConflict();
            }
            throw e;
        }
        return UserResponse.fromEntity(u);
    }

    public UserResponse update(String id, String username, String name, String role, String status, String password) {
        User u = userMapper.getById(id);
        if (u == null) throw BusinessException.userNotFound();
        if (username != null) u.setUsername(username);
        if (name != null) u.setName(name);
        if (role != null) u.setRole(role);
        if (status != null) u.setStatus(status);
        u.setUpdatedAt(Instant.now());
        try {
            int n = userMapper.update(u);
            if (n == 0) throw BusinessException.userNotFound();
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                throw BusinessException.usernameConflict();
            }
            throw e;
        }
        if (password != null && !password.isBlank()) {
            userMapper.updatePassword(id, passwordEncoder.encode(password), u.getUpdatedAt());
        }
        return UserResponse.fromEntity(userMapper.getById(id));
    }

    public UserResponse updateStatus(String id, String status) {
        User u = userMapper.getById(id);
        if (u == null) throw BusinessException.userNotFound();
        Instant now = Instant.now();
        int n = userMapper.updateStatus(id, status, now);
        if (n == 0) throw BusinessException.userNotFound();
        return UserResponse.fromEntity(userMapper.getById(id));
    }
}
