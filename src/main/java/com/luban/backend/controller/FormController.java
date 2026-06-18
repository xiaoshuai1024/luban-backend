package com.luban.backend.controller;

import com.luban.backend.dto.FormResponse;
import com.luban.backend.dto.FormSaveRequest;
import com.luban.backend.service.FormService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 表单管理（管理端，BFF 注入 X-User-ID/X-User-Role）。
 */
@RestController
@RequestMapping("/forms")
public class FormController {

    private final FormService formService;

    public FormController(FormService formService) {
        this.formService = formService;
    }

    @GetMapping
    public List<FormResponse> list(@RequestParam String siteId) {
        return formService.list(siteId);
    }

    @GetMapping("/{id}")
    public FormResponse get(@RequestParam String siteId, @PathVariable String id) {
        return formService.get(siteId, id);
    }

    @PostMapping
    public ResponseEntity<FormResponse> create(@Valid @RequestBody FormSaveRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(formService.create(req));
    }

    @PatchMapping("/{id}")
    public FormResponse update(@RequestParam String siteId, @PathVariable String id, @Valid @RequestBody FormSaveRequest req) {
        return formService.update(siteId, id, req);
    }
}
