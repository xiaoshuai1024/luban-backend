package com.luban.backend.publicside.controller;

import com.luban.backend.publicside.service.PublicTemplateService;
import com.luban.backend.shared.dto.TemplateResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PublicTemplateController — 模板市场公开端 Controller（template-marketplace plan）。
 *
 * <p>路径 /public/templates，免鉴权只读。供 C 端/未登录用户浏览市场目录。
 * 仅返回 published/featured 模板，不含 draft/archived。
 */
@RestController
@RequestMapping("/public/templates")
public class PublicTemplateController {

    private final PublicTemplateService publicTemplateService;

    public PublicTemplateController(PublicTemplateService publicTemplateService) {
        this.publicTemplateService = publicTemplateService;
    }

    /** 市场列表（可选 category 过滤） */
    @GetMapping
    public List<TemplateResponse> list(@RequestParam(required = false) String category) {
        if (category != null && !category.isBlank()) {
            return publicTemplateService.listByCategory(category);
        }
        return publicTemplateService.listMarketplace();
    }

    /** 取模板 schema（仅 published/featured 可见） */
    @GetMapping("/{id}/schema")
    public String getSchema(@PathVariable String id) {
        return publicTemplateService.getSchema(id);
    }
}
