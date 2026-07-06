package com.luban.backend.operatorside.service;

import com.luban.backend.shared.dto.LoginResponse;
import com.luban.backend.shared.dto.UserResponse;
import com.luban.backend.shared.entity.User;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(String username, String password) {
        User u = userRepository.findEntityByUsername(username);
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
        User u = userRepository.findEntityById(userId);
        if (u == null) {
            throw BusinessException.userNotFound();
        }
        return UserResponse.fromEntity(u);
    }
}
