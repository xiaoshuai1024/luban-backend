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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contract test for datasource CRUD + test, aligned with luban-backend-go
 * internal/handler/datasource_handler.go and plan §9.2.
 *
 * <p>Covers every documented status code path so both backends stay parity-locked:
 *   GET    /backend/datasources?siteId=   → 200 [] (multi-tenant filtered)
 *   POST   /backend/datasources           → 201 | 409 NAME_CONFLICT | 404 SITE_NOT_FOUND | 400 INVALID_ARGUMENT
 *   GET    /backend/datasources/:id       → 200 | 404 DATASOURCE_NOT_FOUND
 *   PUT    /backend/datasources/:id       → 200 | 404 | 409
 *   DELETE /backend/datasources/:id       → 204 | 404
 *   POST   /backend/datasources/:id/test  → 200 {ok,message,latencyMs}
 *   Write ops are RequireAdmin (POST/PUT/DELETE); GET and /test are RequireUser.
 *
 * <p>Seed is deterministic — every test recomputes a fresh world in @BeforeEach.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DatasourceContractTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    private static final String SITE_A = "site-ds-a";
    private static final String SITE_B = "site-ds-b";

    @BeforeEach
    void seed() {
        // datasources FK → sites; delete child first.
        jdbc.update("DELETE FROM datasources");
        jdbc.update("DELETE FROM pages");
        jdbc.update("DELETE FROM sites");
        Instant now = Instant.now();
        jdbc.update("INSERT INTO sites (id, name, slug, base_url, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                SITE_A, "Site A", "site-a", "", "active", now, now);
        jdbc.update("INSERT INTO sites (id, name, slug, base_url, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                SITE_B, "Site B", "site-b", "", "active", now, now);
    }

    // ---- GET list ----

    @Test
    void listBySiteIdReturnsOnlyThatSiteDatasources() throws Exception {
        seedDatasource("ds-a-1", SITE_A, "users-api", "api", "{\"url\":\"https://example.com/users\"}");
        seedDatasource("ds-b-1", SITE_B, "users-api", "api", "{\"url\":\"https://example.com/users\"}");

        mockMvc.perform(get("/backend/datasources")
                        .contextPath("/backend")
                        .param("siteId", SITE_A)
                        .header("X-User-ID", "user-001")
                        .header("X-User-Role", "user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("ds-a-1"))
                .andExpect(jsonPath("$[0].siteId").value(SITE_A))
                .andExpect(jsonPath("$[0].config.url").value("https://example.com/users"));
    }

    @Test
    void listWithUnknownSiteIdReturns404SiteNotFound() throws Exception {
        mockMvc.perform(get("/backend/datasources")
                        .contextPath("/backend")
                        .param("siteId", "no-such-site")
                        .header("X-User-ID", "user-001")
                        .header("X-User-Role", "user"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SITE_NOT_FOUND"));
    }

    // ---- POST create ----

    @Test
    void createReturns201ForValidStatic() throws Exception {
        String body = "{\"siteId\":\"" + SITE_A + "\",\"name\":\"cfg\",\"type\":\"static\",\"config\":{\"rows\":[]}}";
        mockMvc.perform(post("/backend/datasources")
                        .contextPath("/backend")
                        .header("X-User-ID", "admin-001")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.siteId").value(SITE_A))
                .andExpect(jsonPath("$.type").value("static"))
                .andExpect(jsonPath("$.config.rows").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void createDuplicateNameReturns409NameConflict() throws Exception {
        seedDatasource("ds-a-1", SITE_A, "dup", "static", "{}");
        String body = "{\"siteId\":\"" + SITE_A + "\",\"name\":\"dup\",\"type\":\"static\",\"config\":{}}";
        mockMvc.perform(post("/backend/datasources")
                        .contextPath("/backend")
                        .header("X-User-ID", "admin-001")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATASOURCE_NAME_CONFLICT"));
    }

    @Test
    void createSameNameDifferentSiteIsAllowed() throws Exception {
        seedDatasource("ds-a-1", SITE_A, "shared", "static", "{}");
        String body = "{\"siteId\":\"" + SITE_B + "\",\"name\":\"shared\",\"type\":\"static\",\"config\":{}}";
        mockMvc.perform(post("/backend/datasources")
                        .contextPath("/backend")
                        .header("X-User-ID", "admin-001")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void createUnknownSiteReturns404SiteNotFound() throws Exception {
        String body = "{\"siteId\":\"no-such-site\",\"name\":\"cfg\",\"type\":\"static\",\"config\":{}}";
        mockMvc.perform(post("/backend/datasources")
                        .contextPath("/backend")
                        .header("X-User-ID", "admin-001")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SITE_NOT_FOUND"));
    }

    @Test
    void createInvalidTypeReturns400InvalidArgument() throws Exception {
        String body = "{\"siteId\":\"" + SITE_A + "\",\"name\":\"cfg\",\"type\":\"mysql\",\"config\":{}}";
        mockMvc.perform(post("/backend/datasources")
                        .contextPath("/backend")
                        .header("X-User-ID", "admin-001")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void createAsNonAdminReturns403PermissionDenied() throws Exception {
        String body = "{\"siteId\":\"" + SITE_A + "\",\"name\":\"cfg\",\"type\":\"static\",\"config\":{}}";
        mockMvc.perform(post("/backend/datasources")
                        .contextPath("/backend")
                        .header("X-User-ID", "user-001")
                        .header("X-User-Role", "user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void createUnauthenticatedReturns401() throws Exception {
        String body = "{\"siteId\":\"" + SITE_A + "\",\"name\":\"cfg\",\"type\":\"static\",\"config\":{}}";
        mockMvc.perform(post("/backend/datasources")
                        .contextPath("/backend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ---- GET single ----

    @Test
    void getExistingReturns200() throws Exception {
        seedDatasource("ds-x", SITE_A, "single", "api", "{\"url\":\"https://example.com\"}");
        mockMvc.perform(get("/backend/datasources/ds-x")
                        .contextPath("/backend")
                        .header("X-User-ID", "user-001")
                        .header("X-User-Role", "user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ds-x"))
                .andExpect(jsonPath("$.siteId").value(SITE_A))
                .andExpect(jsonPath("$.name").value("single"))
                .andExpect(jsonPath("$.config.url").value("https://example.com"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void getWrongSiteReturns404DatasourceNotFound() throws Exception {
        // Multi-tenant guard (plan §7 #4): site B cannot read site A's datasource by id.
        seedDatasource("ds-x", SITE_A, "single", "api", "{\"url\":\"https://example.com\"}");
        mockMvc.perform(get("/backend/datasources/ds-x")
                        .param("siteId", SITE_B)
                        .contextPath("/backend")
                        .header("X-User-ID", "user-001")
                        .header("X-User-Role", "user"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATASOURCE_NOT_FOUND"));
    }

    @Test
    void getMissingReturns404DatasourceNotFound() throws Exception {
        mockMvc.perform(get("/backend/datasources/no-such-id")
                        .contextPath("/backend")
                        .header("X-User-ID", "user-001")
                        .header("X-User-Role", "user"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATASOURCE_NOT_FOUND"));
    }

    // ---- PUT update ----

    @Test
    void updateExistingReturns200() throws Exception {
        seedDatasource("ds-u", SITE_A, "up", "static", "{}");
        String body = "{\"siteId\":\"" + SITE_A + "\",\"name\":\"renamed\",\"type\":\"api\",\"config\":{\"url\":\"https://x\"}}";
        mockMvc.perform(put("/backend/datasources/ds-u")
                        .contextPath("/backend")
                        .header("X-User-ID", "admin-001")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("renamed"))
                .andExpect(jsonPath("$.type").value("api"))
                .andExpect(jsonPath("$.siteId").value(SITE_A))
                .andExpect(jsonPath("$.config.url").value("https://x"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void updateWrongSiteReturns404() throws Exception {
        // Multi-tenant guard: site B cannot update site A's datasource by id.
        seedDatasource("ds-u", SITE_A, "up", "static", "{}");
        String body = "{\"siteId\":\"" + SITE_A + "\",\"name\":\"renamed\",\"type\":\"api\",\"config\":{\"url\":\"https://x\"}}";
        mockMvc.perform(put("/backend/datasources/ds-u")
                        .param("siteId", SITE_B)
                        .contextPath("/backend")
                        .header("X-User-ID", "admin-001")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATASOURCE_NOT_FOUND"));
    }

    @Test
    void updateMissingReturns404() throws Exception {
        String body = "{\"siteId\":\"" + SITE_A + "\",\"name\":\"x\",\"type\":\"static\",\"config\":{}}";
        mockMvc.perform(put("/backend/datasources/no-such")
                        .contextPath("/backend")
                        .header("X-User-ID", "admin-001")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATASOURCE_NOT_FOUND"));
    }

    @Test
    void updateToConflictingNameReturns409() throws Exception {
        seedDatasource("ds-u", SITE_A, "orig", "static", "{}");
        seedDatasource("ds-other", SITE_A, "taken", "static", "{}");
        String body = "{\"siteId\":\"" + SITE_A + "\",\"name\":\"taken\",\"type\":\"static\",\"config\":{}}";
        mockMvc.perform(put("/backend/datasources/ds-u")
                        .contextPath("/backend")
                        .header("X-User-ID", "admin-001")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATASOURCE_NAME_CONFLICT"));
    }

    // ---- DELETE ----

    @Test
    void deleteExistingReturns204() throws Exception {
        seedDatasource("ds-del", SITE_A, "to-remove", "static", "{}");
        mockMvc.perform(delete("/backend/datasources/ds-del")
                        .contextPath("/backend")
                        .header("X-User-ID", "admin-001")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isNoContent());
        // Reverse assertion: post-delete GET → 404 (confirms the row is really gone).
        mockMvc.perform(get("/backend/datasources/ds-del")
                        .contextPath("/backend")
                        .header("X-User-ID", "admin-001")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATASOURCE_NOT_FOUND"));
    }

    @Test
    void deleteWrongSiteReturns404() throws Exception {
        // Multi-tenant guard: site B cannot delete site A's datasource by id.
        seedDatasource("ds-del", SITE_A, "to-remove", "static", "{}");
        mockMvc.perform(delete("/backend/datasources/ds-del")
                        .param("siteId", SITE_B)
                        .contextPath("/backend")
                        .header("X-User-ID", "admin-001")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATASOURCE_NOT_FOUND"));
    }

    @Test
    void deleteMissingReturns404() throws Exception {
        mockMvc.perform(delete("/backend/datasources/no-such")
                        .contextPath("/backend")
                        .header("X-User-ID", "admin-001")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATASOURCE_NOT_FOUND"));
    }

    // ---- POST /:id/test ----

    @Test
    void testStaticReturnsOkWithZeroLatency() throws Exception {
        seedDatasource("ds-t", SITE_A, "static-cfg", "static", "{\"rows\":[]}");
        mockMvc.perform(post("/backend/datasources/ds-t/test")
                        .contextPath("/backend")
                        .header("X-User-ID", "user-001")
                        .header("X-User-Role", "user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.latencyMs").value(0));
    }

    @Test
    void testApiMissingUrlReturns503ConnectionFailed() throws Exception {
        seedDatasource("ds-t", SITE_A, "api-no-url", "api", "{\"headers\":{}}");
        mockMvc.perform(post("/backend/datasources/ds-t/test")
                        .contextPath("/backend")
                        .header("X-User-ID", "user-001")
                        .header("X-User-Role", "user"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DATASOURCE_CONNECTION_FAILED"));
    }

    @Test
    void testMissingReturns404() throws Exception {
        mockMvc.perform(post("/backend/datasources/no-such/test")
                        .contextPath("/backend")
                        .header("X-User-ID", "user-001")
                        .header("X-User-Role", "user"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATASOURCE_NOT_FOUND"));
    }

    // ---- helper ----

    private void seedDatasource(String id, String siteId, String name, String type, String configJson) {
        Instant now = Instant.now();
        jdbc.update("INSERT INTO datasources (id, site_id, name, type, config_json, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, siteId, name, type, configJson, now, now);
    }
}
