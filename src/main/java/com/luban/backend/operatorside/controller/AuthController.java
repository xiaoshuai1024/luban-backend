package com.luban.backend.operatorside.controller;

import com.luban.backend.shared.auth.UserContext;
import com.luban.backend.shared.dto.LoginRequest;
import com.luban.backend.shared.dto.LoginResponse;
import com.luban.backend.shared.dto.UserResponse;
import com.luban.backend.operatorside.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req.username(), req.password()));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        String userId = UserContext.getUserId();
        return ResponseEntity.ok(authService.me(userId));
    }
}
