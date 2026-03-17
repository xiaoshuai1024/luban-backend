package com.luban.backend.controller;

import com.luban.backend.dto.*;
import com.luban.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserListResponse list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return userService.list(page, size, keyword);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable String id) {
        return userService.get(id);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest req) {
        UserResponse created = userService.create(
            req.username(),
            req.password(),
            req.name(),
            req.role()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable String id, @RequestBody UserUpdateRequest req) {
        return userService.update(
            id,
            req != null ? req.username() : null,
            req != null ? req.name() : null,
            req != null ? req.role() : null,
            req != null ? req.status() : null,
            req != null ? req.password() : null
        );
    }

    @PatchMapping("/{id}/status")
    public UserResponse updateStatus(@PathVariable String id, @Valid @RequestBody UserStatusRequest req) {
        return userService.updateStatus(id, req.status());
    }
}
