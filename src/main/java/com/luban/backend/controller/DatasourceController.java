package com.luban.backend.controller;

import com.luban.backend.dto.DatasourceResponse;
import com.luban.backend.dto.DatasourceSaveRequest;
import com.luban.backend.dto.DatasourceTestResult;
import com.luban.backend.service.DatasourceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for datasources. Path prefix is {@code /datasources} (the
 * {@code /backend} context-path is added by Spring via server.servlet.context-path);
 * list is filtered by the {@code siteId} query parameter for multi-tenant isolation.
 *
 * <p>Aligned with luban-backend-go router/router.go datasources group. Auth is
 * RequireUser for all routes; write operations (POST/PUT/DELETE) are additionally
 * RequireAdmin via AuthFilter's {@code ADMIN_DATASOURCES} pattern.
 */
@RestController
@RequestMapping("/datasources")
public class DatasourceController {

    private final DatasourceService datasourceService;

    public DatasourceController(DatasourceService datasourceService) {
        this.datasourceService = datasourceService;
    }

    @GetMapping
    public List<DatasourceResponse> list(@RequestParam(value = "siteId", required = false) String siteId) {
        return datasourceService.list(siteId);
    }

    @GetMapping("/{id}")
    public DatasourceResponse get(@PathVariable String id,
                                  @RequestParam(value = "siteId", required = false) String siteId) {
        return datasourceService.get(id, siteId);
    }

    @PostMapping
    public ResponseEntity<DatasourceResponse> create(@Valid @RequestBody DatasourceSaveRequest req) {
        DatasourceResponse created = datasourceService.create(
                req.siteId(),
                req.name(),
                req.type(),
                req.config()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public DatasourceResponse update(@PathVariable String id,
                                     @RequestParam(value = "siteId", required = false) String siteId,
                                     @Valid @RequestBody DatasourceSaveRequest req) {
        return datasourceService.update(
                id,
                siteId != null ? siteId : req.siteId(),
                req.name(),
                req.type(),
                req.config()
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id,
                                       @RequestParam(value = "siteId", required = false) String siteId) {
        datasourceService.delete(id, siteId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    public DatasourceTestResult test(@PathVariable String id) {
        return datasourceService.testConnection(id);
    }
}
