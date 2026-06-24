package com.luban.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.dto.PageVersionResponse;
import com.luban.backend.entity.Page;
import com.luban.backend.entity.PageVersion;
import com.luban.backend.exception.BusinessException;
import com.luban.backend.mapper.PageMapper;
import com.luban.backend.mapper.PageVersionMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * V2-T8 版本历史服务。
 *
 * 快照触发：PageService.save 时调用 createSnapshot（每次保存生成一条）。
 * 回滚语义（plan §9.2）：读 versionId 的 schema → 覆盖 page.schema_json
 *   → 新建一条 version（versionNo 自增）→ 返回新版本（复制语义非指针）。
 * 保留策略：每页最近 50 版，超出的旧版本清理。
 */
@Service
public class PageVersionService {

    private final PageVersionMapper versionMapper;
    private final PageMapper pageMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int KEEP_RECENT = 50;

    public PageVersionService(PageVersionMapper versionMapper, PageMapper pageMapper) {
        this.versionMapper = versionMapper;
        this.pageMapper = pageMapper;
    }

    /** 列出版本（不含 schema，轻量列表） */
    public List<PageVersionResponse> list(String siteId, String pageId) {
        Page page = pageMapper.getByIdAndSiteId(pageId, siteId);
        if (page == null) throw BusinessException.pageNotFound();
        return versionMapper.listByPageId(pageId).stream()
            .map(v -> PageVersionResponse.fromEntity(v, false))
            .collect(Collectors.toList());
    }

    /** 取单版本（含 schema，详情/回滚前预览用） */
    public PageVersionResponse get(String siteId, String pageId, String versionId) {
        Page page = pageMapper.getByIdAndSiteId(pageId, siteId);
        if (page == null) throw BusinessException.pageNotFound();
        PageVersion v = versionMapper.getByIdAndPageId(versionId, pageId);
        if (v == null) throw new BusinessException(
            org.springframework.http.HttpStatus.NOT_FOUND, "PAGE_VERSION_NOT_FOUND", "版本不存在");
        return PageVersionResponse.fromEntity(v, true);
    }

    /**
     * 回滚：读 versionId schema → 覆盖 page.schema_json → 新建一条 version（versionNo 自增）。
     * 返回新版本（复制语义）。回滚动作本身也产生一条历史记录。
     */
    public PageVersionResponse rollback(String siteId, String pageId, String versionId, String createdBy) {
        Page page = pageMapper.getByIdAndSiteId(pageId, siteId);
        if (page == null) throw BusinessException.pageNotFound();
        PageVersion target = versionMapper.getByIdAndPageId(versionId, pageId);
        if (target == null) throw new BusinessException(
            org.springframework.http.HttpStatus.NOT_FOUND, "PAGE_VERSION_NOT_FOUND", "版本不存在");

        // 覆盖 page.schema_json 为目标版本的 schema
        page.setSchemaJson(target.getSchemaJson());
        page.setUpdatedAt(Instant.now());
        pageMapper.update(page);

        // 新建一条 version（复制语义，versionNo 自增）
        PageVersion snapshot = buildSnapshot(pageId, target.getSchemaJson(),
            "回滚到 v" + target.getVersionNo(), createdBy);
        versionMapper.insert(snapshot);
        pruneOldVersions(pageId);
        return PageVersionResponse.fromEntity(snapshot, true);
    }

    /**
     * 保存时创建快照（由 PageService 调用）。
     */
    public PageVersionResponse createSnapshot(String pageId, JsonNode schema, String summary, String createdBy) {
        String schemaJson;
        try {
            schemaJson = schema != null ? objectMapper.writeValueAsString(schema) : "{}";
        } catch (Exception e) {
            schemaJson = "{}";
        }
        PageVersion snapshot = buildSnapshot(pageId, schemaJson, summary, createdBy);
        versionMapper.insert(snapshot);
        pruneOldVersions(pageId);
        return PageVersionResponse.fromEntity(snapshot, false);
    }

    private PageVersion buildSnapshot(String pageId, String schemaJson, String summary, String createdBy) {
        PageVersion v = new PageVersion();
        v.setId(UUID.randomUUID().toString());
        v.setPageId(pageId);
        int nextNo = versionMapper.maxVersionNo(pageId) + 1;
        v.setVersionNo(nextNo);
        v.setSchemaJson(schemaJson);
        v.setSummary(summary);
        v.setCreatedBy(createdBy);
        v.setCreatedAt(Instant.now());
        return v;
    }

    /** 保留最近 KEEP_RECENT 版，清理更旧的 */
    private void pruneOldVersions(String pageId) {
        int maxNo = versionMapper.maxVersionNo(pageId);
        int keepFromVersion = maxNo - KEEP_RECENT + 1;
        if (keepFromVersion > 1) {
            versionMapper.deleteOlderThan(pageId, keepFromVersion);
        }
    }
}
