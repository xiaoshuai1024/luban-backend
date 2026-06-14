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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contract test for PUT /backend/users/{id} password handling, aligned with
 * luban-backend-go internal/handler/user_handler.go Update() + service behavior.
 *
 *   PUT /backend/users/{id}  (X-User-ID admin required)
 *     - body may carry password; the response MUST NOT contain a password field
 *     - when password present: re-hash and persist (original hash replaced)
 *     - when password absent/blank: existing hash is preserved (not cleared)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserPasswordContractTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String USER_ID = "user-pw-001";
    private String originalHash;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM users");
        originalHash = passwordEncoder.encode("original-secret");
        Instant now = Instant.now();
        jdbc.update("INSERT INTO users (id, username, name, role, status, password, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                USER_ID, "bob", "Bob", "user", "active", originalHash, now, now);
    }

    @Test
    void updateWithoutPasswordKeepsExistingHash() throws Exception {
        // No password field in body → existing password hash MUST be unchanged.
        String body = "{\"username\":\"bob\",\"name\":\"Bob Renamed\",\"role\":\"user\",\"status\":\"active\"}";
        mockMvc.perform(put("/backend/users/{id}", USER_ID)
                        .contextPath("/backend")
                        .header("X-User-ID", "admin-001")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.name").value("Bob Renamed"))
                .andExpect(jsonPath("$.password").doesNotExist());

        String storedHash = jdbc.queryForObject(
                "SELECT password FROM users WHERE id = ?",
                String.class, USER_ID);
        assertThat(storedHash).isEqualTo(originalHash);
        // And the original password still verifies.
        assertThat(passwordEncoder.matches("original-secret", storedHash)).isTrue();
    }

    @Test
    void updateWithPasswordReplacesHash() throws Exception {
        // Password present in body → re-hash and persist; response still has no password.
        String body = "{\"username\":\"bob\",\"name\":\"Bob\",\"role\":\"user\",\"status\":\"active\"," +
                      "\"password\":\"new-secret-456\"}";
        mockMvc.perform(put("/backend/users/{id}", USER_ID)
                        .contextPath("/backend")
                        .header("X-User-ID", "admin-001")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist());

        String storedHash = jdbc.queryForObject(
                "SELECT password FROM users WHERE id = ?",
                String.class, USER_ID);
        // Hash changed (re-hashed), and new password verifies.
        assertThat(storedHash).isNotEqualTo(originalHash);
        assertThat(passwordEncoder.matches("new-secret-456", storedHash)).isTrue();
        // Old password no longer verifies.
        assertThat(passwordEncoder.matches("original-secret", storedHash)).isFalse();
    }

    @Test
    void getUserResponseNeverExposesPassword() throws Exception {
        mockMvc.perform(get("/backend/users/{id}", USER_ID)
                        .contextPath("/backend")
                        .header("X-User-ID", "admin-001")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.password").doesNotExist());
    }
}
