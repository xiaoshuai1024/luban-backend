package com.luban.backend.operatorside.controller;

import com.luban.backend.shared.dto.LeadResponse;
import com.luban.backend.shared.dto.LeadStatusUpdateRequest;
import com.luban.backend.operatorside.service.LeadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 线索中心（管理端，BFF 注入 X-User-ID/X-User-Role）。
 * 多租户隔离：所有查询按 siteId 过滤。
 */
@RestController
@RequestMapping("/leads")
public class LeadController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam String siteId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "formId", required = false) String formId,
            @RequestParam(value = "assigneeId", required = false) String assigneeId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return leadService.list(siteId, status, formId, assigneeId, keyword, page, size);
    }

    @GetMapping("/{id}")
    public LeadResponse get(@RequestParam String siteId, @PathVariable String id) {
        return leadService.get(siteId, id);
    }

    /**
     * 解密查看完整联系方式（T-be-5，安全敏感）：写审计日志，返回明文。
     * 鉴权由 BFF 注入 X-User-ID；多租户按 siteId 隔离。
     */
    @GetMapping("/{id}/contact")
    public Map<String, String> getContact(
            @RequestParam String siteId,
            @PathVariable String id,
            @RequestHeader(value = "X-User-ID", required = false) String actorId) {
        return leadService.getContact(siteId, id, actorId);
    }

    @PatchMapping("/{id}/status")
    public LeadResponse transitStatus(
            @RequestParam String siteId,
            @PathVariable String id,
            @Valid @RequestBody LeadStatusUpdateRequest req,
            @RequestHeader(value = "X-User-ID", required = false) String actorId) {
        return leadService.transitStatus(siteId, id, req.status(), req.assigneeId() != null ? req.assigneeId() : actorId);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam String siteId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "formId", required = false) String formId,
            @RequestParam(value = "assigneeId", required = false) String assigneeId,
            @RequestHeader(value = "X-User-ID", required = false) String actorId) {
        String csv = leadService.exportCsv(siteId, status, formId, assigneeId, actorId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=leads.csv");
        return new ResponseEntity<>(csv.getBytes(StandardCharsets.UTF_8), headers, org.springframework.http.HttpStatus.OK);
    }
}
