package com.luban.backend.controller;

import com.luban.backend.dto.LeadSubmitRequest;
import com.luban.backend.dto.LeadSubmitResult;
import com.luban.backend.service.LeadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/**
 * 公开留资提交（免用户鉴权；防刷在 service 层）。
 * POST /backend/lead/forms/{formId}/submit
 * <p>🟡 IP 安全：仅在配置了可信代理时信任 X-Forwarded-For；否则用 RemoteAddr。
 * 生产必须部署在 BFF/反向代理之后，由代理覆写该 header。
 */
@RestController
@RequestMapping("/lead/forms")
public class PublicLeadController {

    private final LeadService leadService;
    /** 可信代理标记（非空时信任 X-Forwarded-For）。默认空=不信任。 */
    @Value("${app.trusted-proxy:}")
    private String trustedProxy;

    public PublicLeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @PostMapping("/{formId}/submit")
    public LeadSubmitResult submit(
            @PathVariable String formId,
            @Valid @RequestBody LeadSubmitRequest body,
            @RequestHeader(value = "X-Forwarded-For", required = false) String xff,
            @RequestHeader(value = "X-Visitor-ID", required = false) String visitorId,
            HttpServletRequest httpRequest) {
        String ip = resolveIp(xff, httpRequest);
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

    /** 仅在配置可信代理时信任 XFF；否则取直连 RemoteAddr（不可伪造）。 */
    private String resolveIp(String xff, HttpServletRequest req) {
        if (trustedProxy != null && !trustedProxy.isBlank() && xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
