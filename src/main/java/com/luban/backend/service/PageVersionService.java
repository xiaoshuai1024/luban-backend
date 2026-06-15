package com.luban.backend.service;

import com.luban.backend.dto.PageVersionResponse;
import com.luban.backend.entity.Page;
import com.luban.backend.entity.PageVersion;
import com.luban.backend.exception.BusinessException;
import com.luban.backend.mapper.PageMapper;
import com.luban.backend.mapper.PageVersionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 页面版本领域服务（plan §3.4）：
 * <ul>
 *   <li>发布即建版本：每次 published 状态变更 → 自增 version，快照 schema_json</li>
 *   <li>回滚 = 复制目标版本 schema 为当前页面 + 建新版本（不改历史）</li>
 *   <li>版本号单调递增，不复用</li>
 * </ul>
 */
@Service
public class PageVersionService {

    private final PageVersionMapper pageVersionMapper;
    private final PageMapper pageMapper;

    public PageVersionService(PageVersionMapper pageVersionMapper, PageMapper pageMapper) {
        this.pageVersionMapper = pageVersionMapper;
        this.pageMapper = pageMapper;
    }

    /** 版本列表（按版本号倒序）。 */
    public List<PageVersionResponse> list(String siteId, String pageId) {
        ensurePageExists(siteId, pageId);
        return pageVersionMapper.listByPageId(siteId, pageId).stream()
                .map(PageVersionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /** 版本详情。 */
    public PageVersionResponse get(String siteId, String pageId, int version) {
        ensurePageExists(siteId, pageId);
        PageVersion v = pageVersionMapper.getByPageIdAndVersion(siteId, pageId, version);
        if (v == null) throw BusinessException.pageVersionNotFound();
        return PageVersionResponse.fromEntity(v);
    }

    /**
     * 回滚到指定版本：复制目标版本 schema 为当前页面 schema + 建新版本（不改历史，plan §3.4）。
     *
     * @return 新建的版本号
     */
    @Transactional(rollbackFor = Exception.class)
    public PageVersionResponse rollback(String siteId, String pageId, int targetVersion, String operatorId) {
        Page page = ensurePageExists(siteId, pageId);
        PageVersion target = pageVersionMapper.getByPageIdAndVersion(siteId, pageId, targetVersion);
        if (target == null) throw BusinessException.pageVersionNotFound();

        // 1. 把目标版本的 schema 写回当前页面
        page.setSchemaJson(target.getSchemaJson());
        page.setUpdatedAt(Instant.now());
        pageMapper.update(page);

        // 2. 以回滚后的 schema 建一个新版本（不改历史，版本号单调递增）
        return createVersion(siteId, pageId, target.getSchemaJson(), operatorId);
    }

    /**
     * 发布时建版本快照（供 PageService 在 published 状态变更时调用）。
     * 幂等：同一 schema 重复发布会再建一个新版本号（版本号单调递增，不复用）。
     */
    @Transactional(rollbackFor = Exception.class)
    public PageVersionResponse createVersion(String siteId, String pageId, String schemaJson, String operatorId) {
        int nextVersion = pageVersionMapper.maxVersion(siteId, pageId) + 1;
        PageVersion v = new PageVersion();
        v.setId(UUID.randomUUID().toString());
        v.setSiteId(siteId);
        v.setPageId(pageId);
        v.setVersion(nextVersion);
        v.setSchemaJson(schemaJson);
        v.setOperatorId(operatorId);
        v.setCreatedAt(Instant.now());
        pageVersionMapper.insert(v);
        return PageVersionResponse.fromEntity(v);
    }

    private Page ensurePageExists(String siteId, String pageId) {
        Page page = pageMapper.getByIdAndSiteId(pageId, siteId);
        if (page == null) throw BusinessException.pageNotFound();
        return page;
    }
}
