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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contract tests for the public published-page endpoint, aligned field-by-field
 * with luban-backend-go internal/handler/public_handler.go.
 *
 *   GET /backend/public/sites/{slug}/pages?path={path}
 *     - 200 PageResponse (nested schema object) when a published page exists
 *     - 404 SITE_NOT_FOUND  when slug unknown
 *     - 404 PAGE_NOT_FOUND  when no published page at path
 *     - accessible WITHOUT X-User-ID header (no auth)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicPageContractTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    private static final String SITE_ID = "site-001";
    private static final String SLUG = "demo-site";

    @BeforeEach
    void seed() {
        // pages + datasources FK → sites; delete children first (datasources is a new
        // child table added by W1-T2; without this the DELETE FROM sites below violates
        // fk_datasources_site when DatasourceContractTest ran earlier in the same JVM).
        jdbc.update("DELETE FROM datasources");
        jdbc.update("DELETE FROM pages");
        jdbc.update("DELETE FROM sites");
        Instant now = Instant.now();
        jdbc.update("INSERT INTO sites (id, name, slug, base_url, status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                SITE_ID, "Demo Site", SLUG, "https://demo.example.com", "active", now, now);

        // published page at /home
        insertPage("page-pub", SITE_ID, "Home", "/home", "published",
                "{\"components\":[{\"type\":\"hero\",\"props\":{\"title\":\"Hello\"}}]}");
        // draft page at /draft — must NOT be returned by public endpoint
        insertPage("page-draft", SITE_ID, "Draft Page", "/draft", "draft",
                "{\"components\":[]}");
    }

    private void insertPage(String id, String siteId, String name, String path, String status, String schemaJson) {
        Instant now = Instant.now();
        jdbc.update("INSERT INTO pages (id, site_id, name, path, status, schema_json, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, siteId, name, path, status, schemaJson, now, now);
    }

    @Test
    void returnsPublishedPageWithNestedSchema() throws Exception {
        mockMvc.perform(get("/backend/public/sites/{slug}/pages", SLUG)
                        .contextPath("/backend")
                        .param("path", "/home")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("page-pub"))
                .andExpect(jsonPath("$.siteId").value(SITE_ID))
                .andExpect(jsonPath("$.name").value("Home"))
                .andExpect(jsonPath("$.path").value("/home"))
                .andExpect(jsonPath("$.status").value("published"))
                // schema MUST be a nested object (not a base64 string, not a quoted string)
                .andExpect(jsonPath("$.schema.components").isArray())
                .andExpect(jsonPath("$.schema.components[0].type").value("hero"))
                .andExpect(jsonPath("$.schema.components[0].props.title").value("Hello"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                // password-like fields must not leak (PageResponse has none, but assert defensively)
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void siteNotFoundReturns404SiteNotFound() throws Exception {
        mockMvc.perform(get("/backend/public/sites/{slug}/pages", "missing-slug")
                        .contextPath("/backend")
                        .param("path", "/home"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SITE_NOT_FOUND"));
    }

    @Test
    void noPublishedPageAtPathReturns404PageNotFound() throws Exception {
        mockMvc.perform(get("/backend/public/sites/{slug}/pages", SLUG)
                        .contextPath("/backend")
                        .param("path", "/draft"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAGE_NOT_FOUND"));
    }

    @Test
    void accessibleWithoutXUserIdHeader() throws Exception {
        // No X-User-ID header — AuthFilter must allow /backend/public/* through.
        mockMvc.perform(get("/backend/public/sites/{slug}/pages", SLUG)
                        .contextPath("/backend")
                        .param("path", "/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("page-pub"));
    }
}
