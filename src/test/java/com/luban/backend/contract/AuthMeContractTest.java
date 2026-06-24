package com.luban.backend.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contract test for GET /backend/auth/me, aligned with luban-backend-go
 * internal/handler/auth_handler.go Me().
 *
 *   GET /backend/auth/me  (X-User-ID header required by AuthFilter)
 *     - 200 UserResponse with full fields, NO password field
 *     - fields: id / username / name / role / status / createdAt / updatedAt
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthMeContractTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String USER_ID = "user-001";

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM users");
        Instant now = Instant.now();
        jdbc.update("INSERT INTO users (id, username, name, role, status, password, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                USER_ID, "alice", "Alice Lee", "admin", "active",
                passwordEncoder.encode("secret-pw"), now, now);
    }

    @Test
    void meReturnsFullUserResponseWithoutPassword() throws Exception {
        mockMvc.perform(get("/backend/auth/me")
                        .contextPath("/backend")
                        .header("X-User-ID", USER_ID)
                        .header("X-User-Role", "admin")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.name").value("Alice Lee"))
                .andExpect(jsonPath("$.role").value("admin"))
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                // CRITICAL: password must NEVER appear in the response.
                .andExpect(jsonPath("$.password").doesNotExist());
    }
}
