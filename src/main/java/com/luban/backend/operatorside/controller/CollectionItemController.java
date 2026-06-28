package com.luban.backend.operatorside.controller;

import com.luban.backend.shared.dto.CollectionItemResponse;
import com.luban.backend.shared.dto.CollectionItemSaveRequest;
import com.luban.backend.operatorside.service.CollectionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * V2-T7 CollectionItem 内容项控制器（嵌套在 /collections/{id}/items 下）。
 */
@RestController
@RequestMapping("/collections/{collectionId}/items")
public class CollectionItemController {

    private final CollectionService collectionService;

    public CollectionItemController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @GetMapping
    public List<CollectionItemResponse> list(
            @PathVariable String collectionId,
            @RequestParam("siteId") String siteId) {
        return collectionService.listItems(siteId, collectionId);
    }

    @GetMapping("/{itemId}")
    public CollectionItemResponse get(
            @PathVariable String collectionId,
            @PathVariable String itemId,
            @RequestParam("siteId") String siteId) {
        return collectionService.getItem(siteId, collectionId, itemId);
    }

    @PostMapping
    public ResponseEntity<CollectionItemResponse> create(
            @PathVariable String collectionId,
            @RequestParam("siteId") String siteId,
            @Valid @RequestBody CollectionItemSaveRequest req) {
        CollectionItemResponse created = collectionService.createItem(siteId, collectionId, req.data(), req.status());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{itemId}")
    public CollectionItemResponse update(
            @PathVariable String collectionId,
            @PathVariable String itemId,
            @RequestParam("siteId") String siteId,
            @Valid @RequestBody CollectionItemSaveRequest req) {
        return collectionService.updateItem(siteId, collectionId, itemId, req.data(), req.status());
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> delete(
            @PathVariable String collectionId,
            @PathVariable String itemId,
            @RequestParam("siteId") String siteId) {
        collectionService.deleteItem(siteId, collectionId, itemId);
        return ResponseEntity.noContent().build();
    }
}
