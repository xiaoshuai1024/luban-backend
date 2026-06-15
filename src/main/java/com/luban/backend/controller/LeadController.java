package com.luban.backend.controller;

import com.luban.backend.dto.LeadResponse;
import com.luban.backend.dto.LeadStatusUpdateRequest;
import com.luban.backend.service.LeadService;
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
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return leadService.list(siteId, status, formId, assigneeId, page, size);
    }

    @GetMapping("/{id}")
    public LeadResponse get(@RequestParam String siteId, @PathVariable String id) {
        return leadService.get(siteId, id);
    }

    @PatchMapping("/{id}/status")
    public LeadResponse transitStatus(
            @RequestParam String siteId,
            @PathVariable String id,
            @RequestBody LeadStatusUpdateRequest req,
            @RequestHeader(value = "X-User-ID", required = false) String actorId) {
        return leadService.transitStatus(siteId, id, req.status(), req.assigneeId() != null ? req.assigneeId() : actorId);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam String siteId) {
        String csv = leadService.exportCsv(siteId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=leads.csv");
        return new ResponseEntity<>(csv.getBytes(StandardCharsets.UTF_8), headers, org.springframework.http.HttpStatus.OK);
    }
}
