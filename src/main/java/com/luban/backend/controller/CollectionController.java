package com.luban.backend.controller;

import com.luban.backend.dto.CollectionResponse;
import com.luban.backend.dto.CollectionSaveRequest;
import com.luban.backend.service.CollectionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * V2-T7 Collection CMS 控制器。
 *
 * 路由契约（与 Go 端 + BFF 一致）：
 *   GET/POST /collections?siteId=
 *   GET/PUT/DELETE /collections/{id}?siteId=
 *   GET/POST /collections/{id}/items?siteId=
 *   GET/PUT/DELETE /collections/{id}/items/{itemId}?siteId=
 *
 * 鉴权：写操作 RequireAdmin（由 AuthFilter 拦截器统一处理，本控制器不重复）。
 */
@RestController
@RequestMapping("/collections")
public class CollectionController {

    private final CollectionService collectionService;

    public CollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    // === Collection ===

    @GetMapping
    public List<CollectionResponse> list(@RequestParam("siteId") String siteId) {
        return collectionService.list(siteId);
    }

    @GetMapping("/{id}")
    public CollectionResponse get(@PathVariable String id, @RequestParam("siteId") String siteId) {
        return collectionService.get(siteId, id);
    }

    @PostMapping
    public ResponseEntity<CollectionResponse> create(
            @RequestParam("siteId") String siteId,
            @Valid @RequestBody CollectionSaveRequest req) {
        CollectionResponse created = collectionService.create(siteId, req.name(), req.fieldSchema(), req.status());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public CollectionResponse update(
            @PathVariable String id,
            @RequestParam("siteId") String siteId,
            @Valid @RequestBody CollectionSaveRequest req) {
        return collectionService.update(siteId, id, req.name(), req.fieldSchema(), req.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, @RequestParam("siteId") String siteId) {
        collectionService.delete(siteId, id);
        return ResponseEntity.noContent().build();
    }
}
