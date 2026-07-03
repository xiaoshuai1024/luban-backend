package com.luban.backend.operatorside.controller;

import com.luban.backend.operatorside.service.TemplateService;
import com.luban.backend.shared.dto.TemplateInstallRequest;
import com.luban.backend.shared.dto.TemplateResponse;
import com.luban.backend.shared.dto.TemplateSaveRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * TemplateController — 模板市场运营端 Controller（template-marketplace plan）。
 *
 * <p>路径 /templates，需鉴权（AuthFilter 已按路径 RequireUser 守护）。
 * 发布/归档/推荐操作仅 admin（在 Service 层可加强校验，当前复用 AuthFilter）。
 */
@RestController
@RequestMapping("/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public List<TemplateResponse> list() {
        return templateService.list();
    }

    @GetMapping("/{id}")
    public TemplateResponse get(@PathVariable String id) {
        return templateService.get(id);
    }

    /** 取模板最新版 schema（编辑器消费） */
    @GetMapping("/{id}/schema")
    public String getSchema(@PathVariable String id) {
        return templateService.getSchema(id);
    }

    @PostMapping
    public ResponseEntity<TemplateResponse> create(@Valid @RequestBody TemplateSaveRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.create(req));
    }

    @PutMapping("/{id}")
    public TemplateResponse update(@PathVariable String id, @Valid @RequestBody TemplateSaveRequest req) {
        return templateService.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        templateService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // === 状态机操作 ===

    @PostMapping("/{id}/publish")
    public TemplateResponse publish(@PathVariable String id) {
        return templateService.publish(id);
    }

    @PostMapping("/{id}/archive")
    public TemplateResponse archive(@PathVariable String id) {
        return templateService.archive(id);
    }

    @PostMapping("/{id}/feature")
    public TemplateResponse feature(@PathVariable String id) {
        return templateService.feature(id);
    }

    // === 安装（跨聚合：Template → Page）===

    @PostMapping("/{id}/install")
    public TemplateService.InstallResult install(@PathVariable String id, @Valid @RequestBody TemplateInstallRequest req) {
        return templateService.install(id, req);
    }
}
