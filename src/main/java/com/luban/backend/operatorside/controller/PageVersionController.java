package com.luban.backend.operatorside.controller;

import com.luban.backend.shared.dto.PageVersionResponse;
import com.luban.backend.operatorside.service.PageVersionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * V2-T8 版本历史控制器。
 *
 * 路由（嵌套在 /sites/{siteId}/pages/{pageId}/versions 下）：
 *   GET  /versions               → list（不含 schema）
 *   GET  /versions/{versionId}   → get（含 schema）
 *   POST /versions/{versionId}/rollback → 回滚（admin-only）
 *
 * 回滚语义：读 versionId schema 覆盖 page → 新建一条 version → 返回新版本。
 */
@RestController
@RequestMapping("/sites/{siteId}/pages/{pageId}/versions")
public class PageVersionController {

    private final PageVersionService versionService;

    public PageVersionController(PageVersionService versionService) {
        this.versionService = versionService;
    }

    @GetMapping
    public List<PageVersionResponse> list(
            @PathVariable("siteId") String siteId,
            @PathVariable("pageId") String pageId) {
        return versionService.list(siteId, pageId);
    }

    @GetMapping("/{versionId}")
    public PageVersionResponse get(
            @PathVariable("siteId") String siteId,
            @PathVariable("pageId") String pageId,
            @PathVariable String versionId) {
        return versionService.get(siteId, pageId, versionId);
    }

    @PostMapping("/{versionId}/rollback")
    public ResponseEntity<PageVersionResponse> rollback(
            @PathVariable("siteId") String siteId,
            @PathVariable("pageId") String pageId,
            @PathVariable String versionId,
            @RequestHeader(value = "X-User-ID", required = false) String userId) {
        PageVersionResponse rolled = versionService.rollback(siteId, pageId, versionId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(rolled);
    }
}
