package com.luban.backend.operatorside.service;
import com.luban.backend.operatorside.service.UserService;

import com.luban.backend.shared.dto.UserResponse;
import com.luban.backend.shared.exception.BusinessException;
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
class UserServiceTest {

    @Autowired UserService userService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM users");
    }

    @Test
    void list_returns_all_users() {
        Instant now = Instant.now();
        jdbc.update("INSERT INTO users (id, username, password, name, role, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)",
                "u1", "user1", "hash", "User 1", "admin", "active", now, now);
        jdbc.update("INSERT INTO users (id, username, password, name, role, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)",
                "u2", "user2", "hash", "User 2", "editor", "active", now, now);
        assertEquals(2, userService.list(1, 20, null).list().size());
    }

    @Test
    void get_returns_user_by_id() {
        Instant now = Instant.now();
        jdbc.update("INSERT INTO users (id, username, password, name, role, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)",
                "u-get", "getuser", "hash", "Get", "admin", "active", now, now);
        UserResponse resp = userService.get("u-get");
        assertEquals("getuser", resp.username());
    }

    @Test
    void get_nonexistent_throws() {
        assertThrows(BusinessException.class, () -> userService.get("nonexistent"));
    }

    @Test
    void create_inserts_user() {
        var resp = userService.create("newuser", "password123", "New", "editor");
        assertEquals("newuser", resp.username());
        assertEquals("editor", resp.role());
    }

    @Test
    void update_modifies_user() {
        Instant now = Instant.now();
        jdbc.update("INSERT INTO users (id, username, password, name, role, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)",
                "u-upd", "upduser", "hash", "Old", "viewer", "active", now, now);
        var resp = userService.update("u-upd", "upduser", "Updated Name", "admin", "active", null);
        assertEquals("Updated Name", resp.name());
        assertEquals("admin", resp.role());
    }

    @Test
    void updateStatus_changes_status() {
        Instant now = Instant.now();
        jdbc.update("INSERT INTO users (id, username, password, name, role, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)",
                "u-st", "stuser", "hash", "St", "admin", "active", now, now);
        userService.updateStatus("u-st", "disabled");
        UserResponse resp = userService.get("u-st");
        assertEquals("disabled", resp.status());
    }
}
