package com.luban.backend.controller;

import com.luban.backend.dto.LeadSubmitRequest;
import com.luban.backend.dto.LeadSubmitResult;
import com.luban.backend.service.LeadService;
import org.springframework.web.bind.annotation.*;

/**
 * 公开留资提交（免用户鉴权；防刷在 service 层，IP 由 BFF 注入 X-Forwarded-For）。
 * POST /backend/lead/forms/{formId}/submit
 */
@RestController
@RequestMapping("/lead/forms")
public class PublicLeadController {

    private final LeadService leadService;

    public PublicLeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @PostMapping("/{formId}/submit")
    public LeadSubmitResult submit(
            @PathVariable String formId,
            @RequestBody LeadSubmitRequest body,
            @RequestHeader(value = "X-Forwarded-For", required = false) String ip,
            @RequestHeader(value = "X-Visitor-ID", required = false) String visitorId) {
        LeadSubmitRequest req = new LeadSubmitRequest(
                formId,
                body.contact(),
                body.pageId(),
                body.channelId(),
                body.utm(),
                ip,
                visitorId != null ? visitorId : body.visitorId(),
                body.captchaToken()
        );
        return leadService.submit(req);
    }
}
