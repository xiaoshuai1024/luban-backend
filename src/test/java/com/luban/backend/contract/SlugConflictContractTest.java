package com.luban.backend.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contract test for site slug conflict, aligned with luban-backend-go
 * internal/handler/site_handler.go Create() + slug UNIQUE constraint.
 *
 *   POST /backend/sites  (X-User-ID admin required)
 *     - 201 SiteResponse on success
 *     - 409 SLUG_CONFLICT when slug already exists
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SlugConflictContractTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    private static final String SLUG = "existing-slug";

    @BeforeEach
    void seed() {
        // pages + datasources FK → sites; delete children first to avoid referential integrity violation.
        jdbc.update("DELETE FROM datasources");
        jdbc.update("DELETE FROM pages");
        jdbc.update("DELETE FROM sites");
        Instant now = Instant.now();
        jdbc.update("INSERT INTO sites (id, name, slug, base_url, status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                "site-existing", "Existing", SLUG, "", "active", now, now);
    }

    @Test
    void duplicateSlugReturns409SlugConflict() throws Exception {
        String body = "{\"name\":\"Another\",\"slug\":\"" + SLUG + "\",\"baseUrl\":\"\",\"status\":\"active\"}";
        mockMvc.perform(post("/backend/sites")
                        .contextPath("/backend")
                        .header("X-User-ID", "admin-001")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLUG_CONFLICT"));
    }

    @Test
    void uniqueSlugReturns201Created() throws Exception {
        String body = "{\"name\":\"Brand New\",\"slug\":\"brand-new-slug\",\"baseUrl\":\"\",\"status\":\"active\"}";
        mockMvc.perform(post("/backend/sites")
                        .contextPath("/backend")
                        .header("X-User-ID", "admin-001")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.slug").value("brand-new-slug"));
    }
}
