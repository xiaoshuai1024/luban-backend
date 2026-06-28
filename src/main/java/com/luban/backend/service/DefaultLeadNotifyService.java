package com.luban.backend.service;

import com.luban.backend.entity.Form;
import com.luban.backend.entity.Lead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 默认线索通知实现：异步 Webhook 投递（POST 到 lead.webhook.url），失败记日志不阻塞主流程。
 *
 * <p>{@link #notifyNewLead} 标注 {@code @Async("leadNotifyExecutor")}，在独立线程池执行 HTTP 投递。
 * 调用方（{@code LeadService} 在事务 afterCommit 中调用）所在线程不会因 Webhook 阻塞或超时而受影响。
 * 投递异常被捕获并降级为日志，保证不重放、不抛穿到调用方。
 *
 * <p>构造注入：webhookUrl 来自配置（lead.webhook.url），RestClient 由
 * {@link com.luban.backend.config.LeadNotifyConfig#leadNotifyRestClient()} 提供单例 Bean。
 * 单测可通过 @MockBean / 构造注入 mock RestClient（无需多构造器，规避 Spring 歧义）。
 */
@Service
public class DefaultLeadNotifyService implements LeadNotifyService {

    private static final Logger log = LoggerFactory.getLogger(DefaultLeadNotifyService.class);

    private final String webhookUrl;
    private final RestClient restClient;

    public DefaultLeadNotifyService(
            @Value("${lead.webhook.url:}") String webhookUrl,
            @Qualifier("leadNotifyRestClient") RestClient restClient) {
        this.webhookUrl = webhookUrl;
        this.restClient = restClient;
    }

    @Override
    @Async("leadNotifyExecutor")
    public void notifyNewLead(Lead lead, Form form) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.info("新线索 [form={}, lead={}, site={}]（未配置 Webhook，跳过通知）",
                    form.getId(), lead.getId(), lead.getSiteId());
            return;
        }
        try {
            Map<String, Object> payload = buildPayload(lead, form);
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("新线索 Webhook 投递成功 [url={}, lead={}, site={}]",
                    webhookUrl, lead.getId(), lead.getSiteId());
        } catch (Exception e) {
            // 投递失败：仅记录日志，不重试（P0）；P1 可接入重试队列。
            // 关键：不抛穿，避免污染 @Async 线程或触发不必要的错误处理。
            log.warn("新线索 Webhook 投递失败 [url={}, lead={}, site={}]: {}",
                    webhookUrl, lead.getId(), lead.getSiteId(), e.getMessage());
        }
    }

    /** 构造投递 payload（脱敏前字段直接透传 siteId/formId/leadId，contactJson 已是 AES 密文）。 */
    private Map<String, Object> buildPayload(Lead lead, Form form) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "lead.created");
        payload.put("leadId", lead.getId());
        payload.put("siteId", lead.getSiteId());
        payload.put("formId", lead.getFormId());
        payload.put("pageId", lead.getPageId());
        payload.put("channelId", lead.getChannelId());
        payload.put("status", lead.getStatus());
        // contactJson 已是 AES 加密后的密文（见 LeadCryptoService），Webhook 端需用密钥解密。
        payload.put("contactCipher", lead.getContactJson());
        payload.put("utm", lead.getUtmJson());
        payload.put("createdAt", lead.getCreatedAt());
        return payload;
    }
}
