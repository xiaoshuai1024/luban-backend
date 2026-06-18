package com.luban.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.dto.LeadResponse;
import com.luban.backend.dto.LeadSubmitRequest;
import com.luban.backend.dto.LeadSubmitResult;
import com.luban.backend.entity.Form;
import com.luban.backend.entity.Lead;
import com.luban.backend.exception.BusinessException;
import com.luban.backend.mapper.FormMapper;
import com.luban.backend.mapper.LeadMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lead 线索领域服务：留资提交编排（防刷→去重→加密→入库→通知）+ 线索中心读写 + 导出。
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
    @Transactional(rollbackFor = Exception.class)
    public LeadSubmitResult submit(LeadSubmitRequest req) {
        Form form = formMapper.getById(req.formId());
        if (form == null) {
            throw BusinessException.formNotFound();
        }
        if (!"active".equals(form.getStatus())) {
            throw BusinessException.leadDisabled();
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

    /** 线索中心：列表（分页 + 筛选，contact 脱敏）。 */
    public Map<String, Object> list(String siteId, String status, String formId, String assigneeId, int page, int size) {
        int offset = Math.max(0, (page - 1) * size);
        List<Lead> leads = leadMapper.listByQuery(siteId, status, formId, assigneeId, offset, size);
        int total = leadMapper.countByQuery(siteId, status, formId, assigneeId);
        List<LeadResponse> respList = leads.stream().map(this::toResponse).toList();
        return Map.of("list", respList, "total", total, "page", page, "pageSize", size);
    }

    public LeadResponse get(String siteId, String leadId) {
        return toResponse(getOrThrow(siteId, leadId));
    }

    @Transactional(rollbackFor = Exception.class)
    public LeadResponse transitStatus(String siteId, String leadId, String toStatusRaw, String actorId) {
        Lead lead = getOrThrow(siteId, leadId);
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
        return toResponse(lead);
    }

    /** 导出 CSV（contact 明文，权限由 BFF 保证；销售/运营跟进需明文）。 */
    public String exportCsv(String siteId) {
        List<Lead> leads = leadMapper.listAllForExport(siteId);
        StringBuilder sb = new StringBuilder();
        sb.append("id,phone,email,name,status,assignee,created_at\n");
        for (Lead l : leads) {
            Map<String, String> contact = decryptContact(l);
            sb.append(csv(l.getId())).append(',')
                    .append(csv(contact.get("phone"))).append(',')
                    .append(csv(contact.get("email"))).append(',')
                    .append(csv(contact.get("name"))).append(',')
                    .append(csv(l.getStatus())).append(',')
                    .append(csv(l.getAssigneeId())).append(',')
                    .append(csv(l.getCreatedAt() != null ? l.getCreatedAt().toString() : "")).append('\n');
        }
        return sb.toString();
    }

    private Lead getOrThrow(String siteId, String leadId) {
        Lead lead = leadMapper.getByIdAndSiteId(leadId, siteId);
        if (lead == null) throw BusinessException.leadNotFound();
        return lead;
    }

    /** 转响应：contact 解密后脱敏（phone/email）。 */
    public LeadResponse toResponse(Lead lead) {
        Map<String, String> masked = new LinkedHashMap<>();
        Map<String, String> contact = decryptContact(lead);
        for (Map.Entry<String, String> e : contact.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if ("phone".equalsIgnoreCase(k)) masked.put(k, cryptoService.maskPhone(v));
            else if ("email".equalsIgnoreCase(k)) masked.put(k, cryptoService.maskEmail(v));
            else masked.put(k, v);
        }
        Map<String, String> utm = null;
        if (lead.getUtmJson() != null && !lead.getUtmJson().isBlank()) {
            try {
                utm = objectMapper.readValue(lead.getUtmJson(), Map.class);
            } catch (Exception ignored) {
            }
        }
        String formName = null;
        if (lead.getFormId() != null) {
            Form f = formMapper.getById(lead.getFormId());
            if (f != null) formName = f.getName();
        }
        return new LeadResponse(lead.getId(), lead.getSiteId(), lead.getFormId(), lead.getPageId(),
                lead.getChannelId(), masked, utm, lead.getStatus(), lead.getAssigneeId(), lead.getSourceIp(),
                lead.getCreatedAt(), lead.getUpdatedAt(), lead.getConvertedAt(), formName);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> decryptContact(Lead lead) {
        if (lead.getContactJson() == null || lead.getContactJson().isBlank()) return Map.of();
        try {
            String plain = cryptoService.decrypt(lead.getContactJson());
            return objectMapper.readValue(plain, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
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

    private static String csv(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
