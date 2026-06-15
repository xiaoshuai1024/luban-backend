package com.luban.backend.service;

import com.luban.backend.entity.Form;
import com.luban.backend.entity.Lead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 默认线索通知实现：P0 记录日志 + Webhook 占位（POST 到 LEAD_WEBHOOK_URL，失败不阻塞主流程）。
 * P1 接入真实 Webhook 重试 / 邮件 / 企微。
 */
@Service
public class DefaultLeadNotifyService implements LeadNotifyService {

    private static final Logger log = LoggerFactory.getLogger(DefaultLeadNotifyService.class);

    private final String webhookUrl;

    public DefaultLeadNotifyService(@Value("${lead.webhook.url:}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    @Override
    public void notifyNewLead(Lead lead, Form form) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.info("新线索 [form={}, lead={}, site={}]（未配置 Webhook，跳过通知）",
                    form.getId(), lead.getId(), lead.getSiteId());
            return;
        }
        // P0：Webhook 投递占位；P1 实现带重试的 HTTP 投递
        log.info("新线索 Webhook 待投递 [url={}, lead={}, site={}]", webhookUrl, lead.getId(), lead.getSiteId());
    }
}
