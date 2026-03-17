package com.luban.backend.controller;

import com.luban.backend.dto.SiteCreateRequest;
import com.luban.backend.dto.SiteResponse;
import com.luban.backend.service.SiteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sites")
public class SiteController {

    private final SiteService siteService;

    public SiteController(SiteService siteService) {
        this.siteService = siteService;
    }

    @GetMapping
    public List<SiteResponse> list() {
        return siteService.list();
    }

    @GetMapping("/{id}")
    public SiteResponse get(@PathVariable String id) {
        return siteService.get(id);
    }

    @PostMapping
    public ResponseEntity<SiteResponse> create(@Valid @RequestBody SiteCreateRequest req) {
        SiteResponse created = siteService.create(
            req.name(),
            req.slug(),
            req.baseUrl(),
            req.status()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public SiteResponse update(@PathVariable String id, @Valid @RequestBody SiteCreateRequest req) {
        return siteService.update(id, req.name(), req.slug(), req.baseUrl(), req.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        siteService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
