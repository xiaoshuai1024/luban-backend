package com.luban.backend.service;

import com.luban.backend.dto.SiteResponse;
import com.luban.backend.entity.Site;
import com.luban.backend.exception.BusinessException;
import com.luban.backend.mapper.SiteMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SiteService {

    private final SiteMapper siteMapper;

    public SiteService(SiteMapper siteMapper) {
        this.siteMapper = siteMapper;
    }

    public List<SiteResponse> list() {
        return siteMapper.list().stream().map(SiteResponse::fromEntity).collect(Collectors.toList());
    }

    public SiteResponse get(String id) {
        Site s = siteMapper.getById(id);
        if (s == null) throw BusinessException.siteNotFound();
        return SiteResponse.fromEntity(s);
    }

    public SiteResponse create(String name, String slug, String baseUrl, String status) {
        if (status == null || status.isBlank()) status = "active";
        Site site = new Site();
        site.setId(UUID.randomUUID().toString());
        site.setName(name);
        site.setSlug(slug);
        site.setBaseUrl(baseUrl != null ? baseUrl : "");
        site.setStatus(status);
        Instant now = Instant.now();
        site.setCreatedAt(now);
        site.setUpdatedAt(now);
        try {
            siteMapper.insert(site);
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                throw BusinessException.slugConflict();
            }
            throw e;
        }
        return SiteResponse.fromEntity(site);
    }

    public SiteResponse update(String id, String name, String slug, String baseUrl, String status) {
        Site site = siteMapper.getById(id);
        if (site == null) throw BusinessException.siteNotFound();
        site.setName(name);
        site.setSlug(slug);
        site.setBaseUrl(baseUrl != null ? baseUrl : "");
        site.setStatus(status);
        site.setUpdatedAt(Instant.now());
        try {
            int n = siteMapper.update(site);
            if (n == 0) throw BusinessException.siteNotFound();
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                throw BusinessException.slugConflict();
            }
            throw e;
        }
        return SiteResponse.fromEntity(site);
    }

    public void delete(String id) {
        int n = siteMapper.deleteById(id);
        if (n == 0) throw BusinessException.siteNotFound();
    }
}
