package com.luban.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.dto.PageResponse;
import com.luban.backend.entity.Page;
import com.luban.backend.exception.BusinessException;
import com.luban.backend.mapper.PageMapper;
import com.luban.backend.mapper.SiteMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PageService {

    private final PageMapper pageMapper;
    private final SiteMapper siteMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PageService(PageMapper pageMapper, SiteMapper siteMapper) {
        this.pageMapper = pageMapper;
        this.siteMapper = siteMapper;
    }

    public List<PageResponse> list(String siteId) {
        if (siteMapper.getById(siteId) == null) throw BusinessException.siteNotFound();
        return pageMapper.listBySiteId(siteId).stream().map(PageResponse::fromEntity).collect(Collectors.toList());
    }

    public PageResponse get(String siteId, String pageId) {
        Page p = pageMapper.getByIdAndSiteId(pageId, siteId);
        if (p == null) throw BusinessException.pageNotFound();
        return PageResponse.fromEntity(p);
    }

    public PageResponse create(String siteId, String name, String path, String status, JsonNode schema) {
        if (siteMapper.getById(siteId) == null) throw BusinessException.siteNotFound();
        if (status == null || status.isBlank()) status = "draft";
        String schemaJson = schemaToJson(schema);
        Page page = new Page();
        page.setId(UUID.randomUUID().toString());
        page.setSiteId(siteId);
        page.setName(name);
        page.setPath(path);
        page.setStatus(status);
        page.setSchemaJson(schemaJson);
        Instant now = Instant.now();
        page.setCreatedAt(now);
        page.setUpdatedAt(now);
        try {
            pageMapper.insert(page);
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                throw BusinessException.pagePathConflict();
            }
            throw e;
        }
        return PageResponse.fromEntity(page);
    }

    public PageResponse update(String siteId, String pageId, String name, String path, String status, JsonNode schema) {
        Page page = pageMapper.getByIdAndSiteId(pageId, siteId);
        if (page == null) throw BusinessException.pageNotFound();
        page.setName(name);
        page.setPath(path);
        page.setStatus(status != null ? status : page.getStatus());
        page.setSchemaJson(schemaToJson(schema));
        page.setUpdatedAt(Instant.now());
        try {
            int n = pageMapper.update(page);
            if (n == 0) throw BusinessException.pageNotFound();
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                throw BusinessException.pagePathConflict();
            }
            throw e;
        }
        return PageResponse.fromEntity(page);
    }

    public void delete(String siteId, String pageId) {
        int n = pageMapper.deleteByIdAndSiteId(pageId, siteId);
        if (n == 0) throw BusinessException.pageNotFound();
    }

    private String schemaToJson(JsonNode schema) {
        if (schema == null) return "{}";
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            return "{}";
        }
    }
}
