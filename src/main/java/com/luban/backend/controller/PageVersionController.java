package com.luban.backend.controller;

import com.luban.backend.dto.PageVersionResponse;
import com.luban.backend.service.PageVersionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 页面版本控制器（plan §3.4 / §9.2）：
 * <ul>
 *   <li>GET  /sites/{siteId}/pages/{pageId}/versions — 版本列表</li>
 *   <li>GET  /sites/{siteId}/pages/{pageId}/versions/{version} — 版本详情</li>
 *   <li>POST /sites/{siteId}/pages/{pageId}/versions/{version}/rollback — 回滚</li>
 * </ul>
 * 多租户隔离：按 siteId + pageId 过滤。鉴权由 AuthFilter + BFF 注入 X-User-ID 保证。
 */
@RestController
@RequestMapping("/sites/{siteId}/pages/{pageId}/versions")
public class PageVersionController {

    private final PageVersionService pageVersionService;

    public PageVersionController(PageVersionService pageVersionService) {
        this.pageVersionService = pageVersionService;
    }

    @GetMapping
    public List<PageVersionResponse> list(@PathVariable String siteId, @PathVariable String pageId) {
        return pageVersionService.list(siteId, pageId);
    }

    @GetMapping("/{version}")
    public PageVersionResponse get(@PathVariable String siteId,
                                   @PathVariable String pageId,
                                   @PathVariable int version) {
        return pageVersionService.get(siteId, pageId, version);
    }

    @PostMapping("/{version}/rollback")
    public ResponseEntity<PageVersionResponse> rollback(
            @PathVariable String siteId,
            @PathVariable String pageId,
            @PathVariable int version,
            @RequestHeader(value = "X-User-ID", required = false) String operatorId) {
        PageVersionResponse rolled = pageVersionService.rollback(siteId, pageId, version, operatorId);
        return ResponseEntity.ok(rolled);
    }
}
