package com.luban.backend.operatorside.controller;

import com.luban.backend.shared.auth.UserContext;
import com.luban.backend.shared.dto.PageResponse;
import com.luban.backend.shared.dto.PageSaveRequest;
import com.luban.backend.operatorside.service.PageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sites/{id}/pages")
public class PageController {

    private final PageService pageService;

    public PageController(PageService pageService) {
        this.pageService = pageService;
    }

    @GetMapping
    public List<PageResponse> list(@PathVariable("id") String siteId) {
        return pageService.list(siteId);
    }

    @GetMapping("/{pageId}")
    public PageResponse get(@PathVariable("id") String siteId, @PathVariable String pageId) {
        return pageService.get(siteId, pageId);
    }

    @PostMapping
    public ResponseEntity<PageResponse> create(
            @PathVariable("id") String siteId,
            @Valid @RequestBody PageSaveRequest req) {
        PageResponse created = pageService.create(
            siteId,
            req.name(),
            req.path(),
            req.status(),
            req.schema(),
            req.seo()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{pageId}")
    public PageResponse update(
            @PathVariable("id") String siteId,
            @PathVariable String pageId,
            @Valid @RequestBody PageSaveRequest req) {
        return pageService.update(
            siteId,
            pageId,
            req.name(),
            req.path(),
            req.status(),
            req.schema(),
            req.seo()
        );
    }

    @DeleteMapping("/{pageId}")
    public ResponseEntity<Void> delete(@PathVariable("id") String siteId, @PathVariable String pageId) {
        pageService.delete(siteId, pageId);
        return ResponseEntity.noContent().build();
    }

    // ==================== P0 发布闭环 ====================

    /** 发布页面：草稿 → published_pages 快照 + status=published */
    @PostMapping("/{pageId}/publish")
    public PageResponse publish(@PathVariable("id") String siteId, @PathVariable("pageId") String pageId) {
        return pageService.publish(siteId, pageId, UserContext.getUserId());
    }

    /** 下线页面：删 published_pages 快照 + status=archived */
    @PostMapping("/{pageId}/unpublish")
    public PageResponse unpublish(@PathVariable("id") String siteId, @PathVariable("pageId") String pageId) {
        return pageService.unpublish(siteId, pageId);
    }

    /** 草稿预览：返回 pages 表草稿内容（不读 published_pages） */
    @GetMapping("/{pageId}/preview")
    public PageResponse preview(@PathVariable("id") String siteId, @PathVariable("pageId") String pageId) {
        return pageService.getPreviewDraft(siteId, pageId);
    }
}
