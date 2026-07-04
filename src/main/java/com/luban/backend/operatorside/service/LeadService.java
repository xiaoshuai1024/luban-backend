package com.luban.backend.operatorside.service;
import com.luban.backend.shared.crypto.LeadCryptoService;
import com.luban.backend.shared.support.AntiSpamService;
import com.luban.backend.shared.support.DedupService;
import com.luban.backend.shared.domain.LeadAggregate;
import com.luban.backend.shared.domain.event.LeadSubmittedEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.shared.dto.LeadResponse;
import com.luban.backend.shared.dto.LeadSubmitRequest;
import com.luban.backend.shared.dto.LeadSubmitResult;
import com.luban.backend.shared.port.LeadSubmissionPort;
import com.luban.backend.shared.entity.Form;
import com.luban.backend.shared.entity.Lead;
import com.luban.backend.shared.entity.LeadAuditLog;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.FormMapper;
import com.luban.backend.shared.mapper.LeadAuditLogMapper;
import com.luban.backend.shared.mapper.LeadMapper;
import com.luban.backend.shared.mapper.SiteMapper;
import com.luban.backend.shared.mapper.UserSiteMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lead 线索领域服务（backend-ddd-refactor plan v2 T7）。
 *
 * <p>留资提交编排（防刷→去重→加密→入库→通知事件）+ 线索中心读写 + 导出。
 * 状态机/转换副作用下沉到 {@link LeadAggregate}（合并自删除的 LeadStatusMachine）。
 * afterCommit 通知改为发布 {@link LeadSubmittedEvent}（LeadNotifyHandler AFTER_COMMIT 消费），
 * 替代旧 TransactionSynchronizationManager 手动注册（更优雅、可测试、解耦）。
 */
@Service
public class LeadService implements LeadSubmissionPort {

    /** P0 防刷默认值（P1 从 form.antiSpamJson 解析）。 */
    static final int DEFAULT_RATE_MAX = 5;
    static final int DEFAULT_RATE_WINDOW_SEC = 60;
    private static final List<String> DEFAULT_DEDUP_KEYS = List.of("phone");

    private final FormMapper formMapper;
    private final LeadMapper leadMapper;
    private final SiteMapper siteMapper;
    private final LeadAuditLogMapper leadAuditMapper;
    private final TenantGuardService tenantGuard;
    private final DedupService dedupService;
    private final AntiSpamService antiSpamService;
    private final LeadCryptoService cryptoService;
    private final QuotaService quotaService;
    private final UserSiteMapper userSiteMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LeadService(FormMapper formMapper, LeadMapper leadMapper, SiteMapper siteMapper,
                       LeadAuditLogMapper leadAuditMapper, TenantGuardService tenantGuard,
                       DedupService dedupService, AntiSpamService antiSpamService,
                       LeadCryptoService cryptoService, QuotaService quotaService,
                       UserSiteMapper userSiteMapper, ApplicationEventPublisher eventPublisher) {
        this.formMapper = formMapper;
        this.leadMapper = leadMapper;
        this.siteMapper = siteMapper;
        this.leadAuditMapper = leadAuditMapper;
        this.tenantGuard = tenantGuard;
        this.dedupService = dedupService;
        this.antiSpamService = antiSpamService;
        this.cryptoService = cryptoService;
        this.quotaService = quotaService;
        this.userSiteMapper = userSiteMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 留资提交（公开入口核心编排）。
     * 事务内完成所有 DB 写入；notify 通过 afterCommit 回调在事务提交后执行
     * （修复 🔴 notify-in-txn + 🟡 Spring self-invocation：单一 @Transactional 方法避免代理失效）。
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

        // 0. captcha 校验（T-be-4）：form 配置开启时强制
        boolean captchaRequired = parseCaptchaRequired(form);
        if (captchaRequired) {
            if (!antiSpamService.verifyCaptcha(req.captchaToken())) {
                throw BusinessException.captchaInvalid();
            }
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

        // 2b. OVERWRITE/MERGE 策略落地（T-be-4）
        boolean dedupHit = exists > 0;
        if (dedupHit) {
            if (policy == DedupService.Policy.OVERWRITE) {
                leadMapper.deleteByFormHashInWindow(req.formId(), hash, form.getDedupWindow());
            } else if (policy == DedupService.Policy.MERGE) {
                Lead existing = leadMapper.getByFormHashInWindow(req.formId(), hash, form.getDedupWindow());
                if (existing != null) {
                    Map<String, String> mergedContact = new LinkedHashMap<>(decryptContact(existing));
                    mergedContact.putAll(req.contact());
                    String mergedUtm = mergeUtm(existing.getUtmJson(), req.utm());
                    leadMapper.updateContact(existing.getId(), existing.getSiteId(),
                            cryptoService.encrypt(toJson(mergedContact)), mergedUtm, Instant.now());
                    // 通知：发布 LeadSubmittedEvent（LeadNotifyHandler AFTER_COMMIT 消费，替代旧 afterCommit 钩子）
                    publishLeadSubmitted(existing.getId(), form);
                    return new LeadSubmitResult(existing.getId(), existing.getStatus(), true);
                }
            }
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
                ? LeadAggregate.Status.INVALID.name().toLowerCase()
                : LeadAggregate.Status.NEW.name().toLowerCase());
        lead.setDedupHash(hash);
        lead.setSourceIp(req.ip());
        lead.setVisitorId(req.visitorId());
        Instant now = Instant.now();
        lead.setCreatedAt(now);
        lead.setUpdatedAt(now);
        try {
            leadMapper.insert(lead);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // OVERWRITE 并发竞争：降级为重复标记
            lead.setStatus(LeadAggregate.Status.INVALID.name().toLowerCase());
        }

        // 通知：发布 LeadSubmittedEvent（LeadNotifyHandler AFTER_COMMIT 消费，替代旧 afterCommit 钩子）
        publishLeadSubmitted(lead.getId(), form);

        // v02 T-be-4：留资用量计数（站点 owner 配额）。超限由 QuotaService 抛 429。
        // 注意：在 insert 之后调用，超限会回滚整个事务（@Transactional）。
        String ownerUserId = userSiteMapper.findOwnerUserId(form.getSiteId());
        if (ownerUserId != null) {
            quotaService.checkAndIncrement(ownerUserId, "leads");
        }

        return new LeadSubmitResult(lead.getId(), lead.getStatus(), dedupHit);
    }

    /**
     * 发布 LeadSubmittedEvent（事务提交后由 LeadNotifyHandler AFTER_COMMIT 消费）。
     * 替代旧 TransactionSynchronizationManager.registerSynchronization afterCommit 手动注册
     * ——更优雅、可测试、解耦（handler 已重载 lead+form 调 notifyService）。
     */
    private void publishLeadSubmitted(String leadId, Form form) {
        eventPublisher.publishEvent(new LeadSubmittedEvent(
                leadId, form.getId(), form.getSiteId(), Instant.now()));
    }

    /** 合并 utm：旧值 + 新值（新值覆盖同名 key）。 */
    @SuppressWarnings("unchecked")
    private String mergeUtm(String oldUtmJson, Map<String, String> newUtm) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (oldUtmJson != null && !oldUtmJson.isBlank()) {
            try {
                merged.putAll(objectMapper.readValue(oldUtmJson, Map.class));
            } catch (Exception ignored) { }
        }
        if (newUtm != null) merged.putAll(newUtm);
        return toJson(merged);
    }

    /** 线索中心：列表（分页 + 筛选，contact 脱敏）。校验 siteId 存在以强化多租户隔离（T-be-2）。 */
    public Map<String, Object> list(String siteId, String status, String formId, String assigneeId,
                                    String keyword, int page, int size) {
        ensureSiteExists(siteId);
        // keyword 搜索需匹配加密 contact，须在应用层解密过滤（T-be-3）
        if (keyword != null && !keyword.isBlank()) {
            return listWithKeyword(siteId, status, formId, assigneeId, keyword.trim(), page, size);
        }
        int offset = Math.max(0, (page - 1) * size);
        List<Lead> leads = leadMapper.listByQuery(siteId, status, formId, assigneeId, offset, size);
        int total = leadMapper.countByQuery(siteId, status, formId, assigneeId);
        List<LeadResponse> respList = leads.stream().map(this::toResponse).toList();
        return Map.of("list", respList, "total", total, "page", page, "pageSize", size);
    }

    /**
     * keyword 搜索（T-be-3）：contact 加密无法 SQL LIKE，需拉取后解密匹配。
     * 为控制内存，单次最多扫描 MAX_KEYWORD_SCAN 条，分页在匹配结果上做。
     * 超出扫描上限时返回 truncated=true 提示前端（修复 🔴 total 不准）。
     */
    private Map<String, Object> listWithKeyword(String siteId, String status, String formId, String assigneeId,
                                                String keyword, int page, int size) {
        int scanLimit = 500; // 单次最多扫描 500 条，避免全表解密
        List<Lead> all = leadMapper.listByQuery(siteId, status, formId, assigneeId, 0, scanLimit);
        boolean truncated = all.size() >= scanLimit;
        String kw = keyword.toLowerCase();
        List<LeadResponse> matched = all.stream()
                .map(l -> {
                    // 解密 contact 后做大小写不敏感包含匹配
                    Map<String, String> c = decryptContact(l);
                    boolean hit = c.values().stream()
                            .anyMatch(v -> v != null && v.toLowerCase().contains(kw));
                    return hit ? toResponse(l) : null;
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        int total = matched.size();
        int offset = Math.max(0, (page - 1) * size);
        int end = Math.min(offset + size, total);
        List<LeadResponse> pageList = offset < total ? matched.subList(offset, end) : List.of();
        return Map.of("list", pageList, "total", total, "page", page, "pageSize", size, "truncated", truncated);
    }

    public LeadResponse get(String siteId, String leadId) {
        ensureSiteExists(siteId);
        return toResponse(getOrThrow(siteId, leadId));
    }

    /**
     * 解密查看完整联系方式（T-be-5，安全敏感）。
     * 写 lead_audit_logs(action=VIEW_CONTACT)；返回明文（仅本次响应，不缓存）。
     */
    public Map<String, String> getContact(String siteId, String leadId, String actorId) {
        ensureSiteExists(siteId);
        Lead lead = getOrThrow(siteId, leadId);
        Map<String, String> contact = decryptContact(lead);
        // 审计：记录谁在何时查看了哪条线索的联系方式
        leadAuditMapper.insert(newAuditLog(siteId, leadId, actorId, "VIEW_CONTACT",
                toJson(Map.of("formId", lead.getFormId() != null ? lead.getFormId() : ""))));
        return contact;
    }

    @Transactional(rollbackFor = Exception.class)
    public LeadResponse transitStatus(String siteId, String leadId, String toStatusRaw, String actorId) {
        Lead lead = getOrThrow(siteId, leadId);
        String fromStatus = lead.getStatus();
        // 聚合根 transit：状态机校验 + 副作用（convertedAt/assigneeId）+ 事件（LeadConvertedEvent）
        LeadAggregate agg = LeadAggregate.reconstitute(lead);
        agg.transit(toStatusRaw, actorId);
        Lead updated = agg.toLead();
        leadMapper.updateStatus(leadId, siteId, updated.getStatus(), updated.getAssigneeId(),
                updated.getConvertedAt(), updated.getUpdatedAt());
        // 发布聚合根事件（LeadConvertedEvent → Analytics 归因）
        agg.pullEvents().forEach(eventPublisher::publishEvent);
        // 审计：状态转移
        leadAuditMapper.insert(newAuditLog(siteId, leadId, actorId, "STATUS_TRANSIT",
                toJson(Map.of("from", fromStatus, "to", updated.getStatus()))));
        return toResponse(updated);
    }

    /** 导出 CSV（contact 明文，权限由 BFF 保证；销售/运营跟进需明文）。
     *  支持筛选参数（修复 🔴 export 忽略 filter）。 */
    public String exportCsv(String siteId, String status, String formId, String assigneeId, String actorId) {
        ensureSiteExists(siteId);
        // 审计：记录导出操作（含筛选条件）
        leadAuditMapper.insert(newAuditLog(siteId, null, actorId, "EXPORT",
                toJson(Map.of("status", status == null ? "" : status,
                        "formId", formId == null ? "" : formId,
                        "assigneeId", assigneeId == null ? "" : assigneeId))));
        List<Lead> leads;
        boolean hasFilter = (status != null && !status.isBlank())
                || (formId != null && !formId.isBlank())
                || (assigneeId != null && !assigneeId.isBlank());
        if (hasFilter) {
            leads = leadMapper.listForExport(siteId, status, formId, assigneeId);
        } else {
            leads = leadMapper.listAllForExport(siteId);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("phone,email,name,status,created_at\n");
        for (Lead l : leads) {
            Map<String, String> contact = decryptContact(l);
            sb.append(csv(contact.get("phone"))).append(',')
                    .append(csv(contact.get("email"))).append(',')
                    .append(csv(contact.get("name"))).append(',')
                    .append(csv(l.getStatus())).append(',')
                    .append(csv(l.getCreatedAt() != null ? l.getCreatedAt().toString() : "")).append('\n');
        }
        return sb.toString();
    }

    private Lead getOrThrow(String siteId, String leadId) {
        Lead lead = leadMapper.getByIdAndSiteId(leadId, siteId);
        if (lead == null) throw BusinessException.leadNotFound();
        return lead;
    }

    /** 校验 siteId 存在 + 当前用户有权访问（🟡 tenant authz）。 */
    private void ensureSiteExists(String siteId) {
        if (siteId == null || siteId.isBlank() || siteMapper.getById(siteId) == null) {
            throw BusinessException.siteNotFound();
        }
        tenantGuard.ensureSiteAccess(siteId);
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

    /** 从 form.antiSpamJson 解析是否要求 captcha（T-be-4）。 */
    @SuppressWarnings("unchecked")
    private boolean parseCaptchaRequired(Form form) {
        if (form.getAntiSpamJson() == null || form.getAntiSpamJson().isBlank()) return false;
        try {
            Map<String, Object> cfg = objectMapper.readValue(form.getAntiSpamJson(), Map.class);
            Object v = cfg.get("captchaRequired");
            return Boolean.TRUE.equals(v);
        } catch (Exception e) {
            return false;
        }
    }

    /** 构建审计日志实体（VIEW_CONTACT / STATUS_TRANSIT）。 */
    private LeadAuditLog newAuditLog(String siteId, String leadId, String actorId, String action, String detail) {
        LeadAuditLog log = new LeadAuditLog();
        log.setId(UUID.randomUUID().toString());
        log.setSiteId(siteId);
        log.setLeadId(leadId);
        log.setActorId(actorId != null ? actorId : "system");
        log.setAction(action);
        log.setDetail(detail);
        log.setCreatedAt(Instant.now());
        return log;
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
