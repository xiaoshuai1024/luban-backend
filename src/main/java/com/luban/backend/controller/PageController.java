package com.luban.backend.controller;

import com.luban.backend.dto.PageResponse;
import com.luban.backend.dto.PageSaveRequest;
import com.luban.backend.service.PageService;
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
            req.schema()
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
            req.schema()
        );
    }

    @DeleteMapping("/{pageId}")
    public ResponseEntity<Void> delete(@PathVariable("id") String siteId, @PathVariable String pageId) {
        pageService.delete(siteId, pageId);
        return ResponseEntity.noContent().build();
    }
}
