package com.luban.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.dto.LeadSubmitRequest;
import com.luban.backend.dto.LeadSubmitResult;
import com.luban.backend.entity.Form;
import com.luban.backend.entity.Lead;
import com.luban.backend.exception.BusinessException;
import com.luban.backend.mapper.FormMapper;
import com.luban.backend.mapper.LeadMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lead 线索领域服务：留资提交编排（防刷→去重→加密→入库→通知）。
 *
 * 编排逻辑通过 mock mapper/service 单测覆盖；DB 真实交互由集成测试覆盖。
 */
@Service
public class LeadService {

    /** P0 防刷默认值（P1 从 form.antiSpamJson 解析）。 */
    static final int DEFAULT_RATE_MAX = 5;
    static final int DEFAULT_RATE_WINDOW_SEC = 60;
    private static final List<String> DEFAULT_DEDUP_KEYS = List.of("phone");

    private final FormMapper formMapper;
    private final LeadMapper leadMapper;
    private final DedupService dedupService;
    private final AntiSpamService antiSpamService;
    private final LeadCryptoService cryptoService;
    private final LeadStatusMachine statusMachine;
    private final LeadNotifyService notifyService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LeadService(FormMapper formMapper, LeadMapper leadMapper, DedupService dedupService,
                       AntiSpamService antiSpamService, LeadCryptoService cryptoService,
                       LeadStatusMachine statusMachine, LeadNotifyService notifyService) {
        this.formMapper = formMapper;
        this.leadMapper = leadMapper;
        this.dedupService = dedupService;
        this.antiSpamService = antiSpamService;
        this.cryptoService = cryptoService;
        this.statusMachine = statusMachine;
        this.notifyService = notifyService;
    }

    /**
     * 留资提交（公开入口核心编排）。
     *
     * @throws BusinessException FORM_NOT_FOUND / LEAD_SPAM_BLOCKED / LEAD_DUPLICATE
     */
    public LeadSubmitResult submit(LeadSubmitRequest req) {
        Form form = formMapper.getById(req.formId());
        if (form == null) {
            throw BusinessException.formNotFound();
        }

        // 1. 防刷（IP + form 维度）
        if (antiSpamService.isRateLimited(req.ip(), req.formId(), DEFAULT_RATE_MAX, DEFAULT_RATE_WINDOW_SEC)) {
            throw BusinessException.leadSpamBlocked();
        }

        // 2. 去重
        List<String> dedupKeys = parseDedupKeys(form);
        String hash = dedupService.computeHash(req.formId(), req.contact(), dedupKeys);
        int exists = leadMapper.countByFormHashInWindow(req.formId(), hash, form.getDedupWindow());
        DedupService.Policy policy = parsePolicy(form);
        DedupService.Decision decision = dedupService.decide(exists > 0, policy);
        if (decision == DedupService.Decision.REJECT) {
            throw BusinessException.leadDuplicate();
        }

        // 3. 加密 contact + 构建 lead
        String encryptedContact = cryptoService.encrypt(toJson(req.contact()));
        Lead lead = new Lead();
        lead.setId(UUID.randomUUID().toString());
        lead.setSiteId(form.getSiteId());
        lead.setFormId(form.getId());
        lead.setPageId(req.pageId() != null ? req.pageId() : form.getPageId());
        lead.setContactJson(encryptedContact);
        lead.setUtmJson(toJson(req.utm()));
        lead.setStatus(decision == DedupService.Decision.MARK_DUPLICATE
                ? LeadStatusMachine.Status.INVALID.name().toLowerCase()
                : LeadStatusMachine.Status.NEW.name().toLowerCase());
        lead.setDedupHash(hash);
        lead.setSourceIp(req.ip());
        lead.setVisitorId(req.visitorId());
        Instant now = Instant.now();
        lead.setCreatedAt(now);
        lead.setUpdatedAt(now);
        leadMapper.insert(lead);

        // 4. 通知（失败不阻塞主流程）
        notifyService.notifyNewLead(lead, form);

        return new LeadSubmitResult(lead.getId(), lead.getStatus(), exists > 0);
    }

    private List<String> parseDedupKeys(Form form) {
        if (form.getDedupKeysJson() == null || form.getDedupKeysJson().isBlank()) {
            return DEFAULT_DEDUP_KEYS;
        }
        try {
            List<String> keys = objectMapper.readValue(form.getDedupKeysJson(), List.class);
            return keys.isEmpty() ? DEFAULT_DEDUP_KEYS : keys;
        } catch (Exception e) {
            return DEFAULT_DEDUP_KEYS;
        }
    }

    private DedupService.Policy parsePolicy(Form form) {
        if (form.getDedupPolicy() == null || form.getDedupPolicy().isBlank()) {
            return DedupService.Policy.REJECT;
        }
        try {
            return DedupService.Policy.valueOf(form.getDedupPolicy().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DedupService.Policy.REJECT;
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    // 供 controller 的状态流转方法（P0 最小）
    public Lead get(String siteId, String leadId) {
        Lead lead = leadMapper.getByIdAndSiteId(leadId, siteId);
        if (lead == null) throw BusinessException.leadNotFound();
        return lead;
    }

    public Lead transitStatus(String siteId, String leadId, String toStatusRaw, String actorId) {
        Lead lead = get(siteId, leadId);
        LeadStatusMachine.Status from = statusMachine.parse(lead.getStatus());
        LeadStatusMachine.Status to = statusMachine.parse(toStatusRaw);
        statusMachine.ensureValid(from, to);
        Instant now = Instant.now();
        Instant convertedAt = to == LeadStatusMachine.Status.CONVERTED ? now : lead.getConvertedAt();
        String assignee = (to == LeadStatusMachine.Status.ASSIGNED && actorId != null) ? actorId : lead.getAssigneeId();
        leadMapper.updateStatus(leadId, siteId, to.name().toLowerCase(), assignee, convertedAt, now);
        lead.setStatus(to.name().toLowerCase());
        lead.setAssigneeId(assignee);
        lead.setConvertedAt(convertedAt);
        return lead;
    }

    public Map<String, Object> list(String siteId, String status, String formId, String assigneeId, int page, int size) {
        int offset = Math.max(0, (page - 1) * size);
        List<Lead> list = leadMapper.listByQuery(siteId, status, formId, assigneeId, offset, size);
        int total = leadMapper.countByQuery(siteId, status, formId, assigneeId);
        return Map.of("list", list, "total", total, "page", page, "pageSize", size);
    }
}
