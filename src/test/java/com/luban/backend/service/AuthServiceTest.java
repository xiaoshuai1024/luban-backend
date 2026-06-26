package com.luban.backend.service;

import com.luban.backend.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceTest {

    @Autowired AuthService authService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM users");
    }

    @Test
    void login_with_valid_credentials_returns_user() {
        Instant now = Instant.now();
        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        String hash = encoder.encode("password123");
        jdbc.update("INSERT INTO users (id, username, password, name, role, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)",
                "u-auth", "authuser", hash, "Auth", "admin", "active", now, now);
        var result = authService.login("authuser", "password123");
        assertNotNull(result.user());
        assertEquals("authuser", result.user().username());
        assertNotNull(result.claims());
    }

    @Test
    void login_with_wrong_password_throws() {
        Instant now = Instant.now();
        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        String hash = encoder.encode("password123");
        jdbc.update("INSERT INTO users (id, username, password, name, role, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)",
                "u-auth2", "authuser2", hash, "Auth2", "admin", "active", now, now);
        assertThrows(BusinessException.class, () -> authService.login("authuser2", "wrongpassword"));
    }

    @Test
    void login_with_nonexistent_user_throws() {
        assertThrows(BusinessException.class, () -> authService.login("ghost", "anything"));
    }

    @Test
    void me_returns_user_by_id() {
        Instant now = Instant.now();
        jdbc.update("INSERT INTO users (id, username, password, name, role, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)",
                "u-me", "meuser", "hash", "Me", "admin", "active", now, now);
        var resp = authService.me("u-me");
        assertEquals("meuser", resp.username());
    }
}
