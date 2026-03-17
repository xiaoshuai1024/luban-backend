package com.luban.backend.service;

import com.luban.backend.dto.LoginResponse;
import com.luban.backend.dto.UserResponse;
import com.luban.backend.entity.User;
import com.luban.backend.exception.BusinessException;
import com.luban.backend.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(String username, String password) {
        User u = userMapper.findByUsername(username);
        if (u == null) {
            throw BusinessException.invalidCredentials();
        }
        if (!"active".equals(u.getStatus())) {
            throw BusinessException.userDisabled();
        }
        if (!passwordEncoder.matches(password, u.getPassword())) {
            throw BusinessException.invalidCredentials();
        }
        return LoginResponse.of(UserResponse.fromEntity(u));
    }

    /**
     * Current user for /auth/me (by userId from UserContext). Returns full user from DB.
     */
    public UserResponse me(String userId) {
        User u = userMapper.getById(userId);
        if (u == null) {
            throw BusinessException.userNotFound();
        }
        return UserResponse.fromEntity(u);
    }
}
