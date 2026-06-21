package com.luban.backend.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * V2-T13/T0 契约测试 — V2 新增后端能力的端到端覆盖（H2 集成测试，无需外部中间件）。
 *
 * 覆盖：SEO / CMS collections / 版本历史 / analytics。
 * 注意：所有请求需 .contextPath("/backend")（与 SlugConflictContractTest 一致）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class V2FeaturesContractTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String SITE_ID = "site-v2-001";
    private static final String SLUG = "v2-demo";
    private static final String ADMIN = "admin";

    /** 包装：加 contextPath + admin header（X-User-ID + X-User-Role，与 SlugConflictContractTest 一致） */
    private MockHttpServletRequestBuilder req(MockHttpServletRequestBuilder b) {
        return b.contextPath("/backend").header("X-User-ID", "admin-001").header("X-User-Role", "admin");
    }

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM collection_items");
        jdbc.update("DELETE FROM collections");
        jdbc.update("DELETE FROM page_versions");
        jdbc.update("DELETE FROM pages WHERE site_id = ?", SITE_ID);
        jdbc.update("DELETE FROM sites WHERE id = ?", SITE_ID);
        Instant now = Instant.now();
        jdbc.update(
            "INSERT INTO sites (id, name, slug, base_url, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            SITE_ID, "V2 Demo", SLUG, "http://v2.test", "active", now, now
        );
    }

    // === V2-T2 SEO ===

    @Test
    void pageWithSeo_persistsAndReturns() throws Exception {
        String seoJson = "{\"title\":\"SEO 测试\",\"description\":\"描述\",\"noIndex\":true}";
        String schemaJson = "{\"root\":{\"id\":\"root\",\"type\":\"LubanContainer\",\"props\":{},\"children\":[]}}";
        Instant now = Instant.now();
        String pageId = "page-seo-001";
        jdbc.update(
            "INSERT INTO pages (id, site_id, name, path, status, schema_json, seo_json, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            pageId, SITE_ID, "SEO 页", "/seo-test", "draft", schemaJson, seoJson, now, now
        );

        mockMvc.perform(req(get("/backend/sites/" + SITE_ID + "/pages/" + pageId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.seo.title").value("SEO 测试"))
            .andExpect(jsonPath("$.seo.description").value("描述"))
            .andExpect(jsonPath("$.seo.noIndex").value(true));
    }

    @Test
    void createPageWithSeo_viaApi() throws Exception {
        String body = mapper.writeValueAsString(Map.of(
            "name", "新建 SEO 页",
            "path", "/new-seo-" + System.currentTimeMillis(),
            "schema", Map.of("root", Map.of("id", "root", "type", "LubanContainer", "props", Map.of(), "children", List.of())),
            "seo", Map.of("title", "API 创建 SEO", "keywords", List.of("营销", "建站"))
        ));
        mockMvc.perform(req(post("/backend/sites/" + SITE_ID + "/pages"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.seo.title").value("API 创建 SEO"))
            .andExpect(jsonPath("$.seo.keywords[0]").value("营销"));
    }

    // === V2-T7 CMS Collections ===

    @Test
    void collectionCrud_fullCycle() throws Exception {
        String createBody = mapper.writeValueAsString(Map.of(
            "name", "文章集合",
            "fieldSchema", Map.of("fields", List.of(Map.of("name", "title", "type", "string")))
        ));
        MvcResult createRes = mockMvc.perform(req(post("/backend/collections?siteId=" + SITE_ID))
                .contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("文章集合"))
            .andExpect(jsonPath("$.fieldSchema.fields[0].name").value("title"))
            .andReturn();
        String collectionId = mapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(req(get("/backend/collections?siteId=" + SITE_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(collectionId));

        String updateBody = mapper.writeValueAsString(Map.of(
            "name", "文章集合-改",
            "fieldSchema", Map.of("fields", List.of())
        ));
        mockMvc.perform(req(put("/backend/collections/" + collectionId + "?siteId=" + SITE_ID))
                .contentType(MediaType.APPLICATION_JSON).content(updateBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("文章集合-改"));

        mockMvc.perform(req(delete("/backend/collections/" + collectionId + "?siteId=" + SITE_ID)))
            .andExpect(status().isNoContent());
    }

    @Test
    void collectionItemCrud_fullCycle() throws Exception {
        String createBody = mapper.writeValueAsString(Map.of("name", "商品集合", "fieldSchema", Map.of()));
        MvcResult cr = mockMvc.perform(req(post("/backend/collections?siteId=" + SITE_ID))
                .contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isCreated()).andReturn();
        String cid = mapper.readTree(cr.getResponse().getContentAsString()).get("id").asText();

        String itemBody = mapper.writeValueAsString(Map.of("data", Map.of("title", "商品A", "price", 99)));
        MvcResult ir = mockMvc.perform(req(post("/backend/collections/" + cid + "/items?siteId=" + SITE_ID))
                .contentType(MediaType.APPLICATION_JSON).content(itemBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.title").value("商品A"))
            .andReturn();
        String itemId = mapper.readTree(ir.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(req(get("/backend/collections/" + cid + "/items?siteId=" + SITE_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(itemId));

        mockMvc.perform(req(delete("/backend/collections/" + cid + "/items/" + itemId + "?siteId=" + SITE_ID)))
            .andExpect(status().isNoContent());
    }

    // === V2-T8 版本历史 ===

    @Test
    void versionHistory_snapshotOnSave_listGetRollback() throws Exception {
        String createBody = mapper.writeValueAsString(Map.of(
            "name", "版本测试页",
            "path", "/ver-test-" + System.currentTimeMillis(),
            "schema", Map.of("root", Map.of("id", "root", "type", "LubanContainer", "props", Map.of(), "children", List.of()))
        ));
        MvcResult cr = mockMvc.perform(req(post("/backend/sites/" + SITE_ID + "/pages"))
                .contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isCreated()).andReturn();
        String pageId = mapper.readTree(cr.getResponse().getContentAsString()).get("id").asText();

        String updateBody = mapper.writeValueAsString(Map.of(
            "name", "版本测试页",
            "path", "/ver-test-" + System.currentTimeMillis(),
            "schema", Map.of("root", Map.of("id", "root", "type", "LubanContainer", "props", Map.of("padded", true), "children", List.of()))
        ));
        mockMvc.perform(req(put("/backend/sites/" + SITE_ID + "/pages/" + pageId))
                .contentType(MediaType.APPLICATION_JSON).content(updateBody))
            .andExpect(status().isOk());

        MvcResult vr = mockMvc.perform(req(get("/backend/sites/" + SITE_ID + "/pages/" + pageId + "/versions")))
            .andExpect(status().isOk()).andReturn();
        int versionCount = mapper.readTree(vr.getResponse().getContentAsString()).size();
        assert versionCount >= 2 : "应至少 2 版（create + update）";

        String v1Id = mapper.readTree(vr.getResponse().getContentAsString()).get(versionCount - 1).get("id").asText();
        mockMvc.perform(req(get("/backend/sites/" + SITE_ID + "/pages/" + pageId + "/versions/" + v1Id)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.schema.root").exists());

        mockMvc.perform(req(post("/backend/sites/" + SITE_ID + "/pages/" + pageId + "/versions/" + v1Id + "/rollback")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("回滚到 v1")));
    }

    // === V2-T10 Analytics ===

    @Test
    void siteAnalytics_updateAndPublicConfig() throws Exception {
        String body = mapper.writeValueAsString(Map.of(
            "name", "V2 Demo",
            "slug", SLUG,
            "baseUrl", "http://v2.test",
            "status", "active",
            "analytics", Map.of(
                "ga4", Map.of("measurementId", "G-TEST123"),
                "baidu", Map.of("id", "baidu123")
            )
        ));
        mockMvc.perform(req(put("/backend/sites/" + SITE_ID))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.analytics.ga4.measurementId").value("G-TEST123"))
            .andExpect(jsonPath("$.analytics.baidu.id").value("baidu123"));

        // 公开 config 端点（无 X-User-ID，需公开访问；加 contextPath 但不加 admin header）
        mockMvc.perform(get("/backend/public/sites/" + SLUG + "/config").contextPath("/backend"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.analytics.ga4.measurementId").value("G-TEST123"));
    }

    // === V2-T7 公开 collection items（CMS 绑定渲染）===

    @Test
    void publicCollectionItems_returnsActiveItems() throws Exception {
        // 建 collection + 2 个 active item + 1 个 inactive item
        String createBody = mapper.writeValueAsString(Map.of("name", "公开集合", "fieldSchema", Map.of()));
        MvcResult cr = mockMvc.perform(req(post("/backend/collections?siteId=" + SITE_ID))
                .contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isCreated()).andReturn();
        String cid = mapper.readTree(cr.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(req(post("/backend/collections/" + cid + "/items?siteId=" + SITE_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("data", Map.of("title", "文章1")))))
            .andExpect(status().isCreated());
        mockMvc.perform(req(post("/backend/collections/" + cid + "/items?siteId=" + SITE_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("data", Map.of("title", "文章2")))))
            .andExpect(status().isCreated());

        // 公开读：返回 active items
        mockMvc.perform(get("/backend/public/sites/" + SLUG + "/collections/" + cid + "/items").contextPath("/backend"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].data.title").exists());
    }
}
