package com.luban.backend.shared.domain;

import com.luban.backend.shared.domain.event.DomainEvent;
import com.luban.backend.shared.domain.event.LeadConvertedEvent;
import com.luban.backend.shared.entity.Lead;
import com.luban.backend.shared.exception.BusinessException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lead 聚合根（backend-ddd-refactor plan v2 T7，合并 LeadStatusMachine）。
 *
 * <p>v2 纠正（对齐 {@code .agents/rules/luban-engineering-principles.md} §2 反模式）：
 * 旧 {@code LeadStatusMachine} 是 domain 包内的 @Service（domain 层带框架注解）——反模式。
 * 重写为真聚合根：final + 持有 Lead 引用 + 充血实例方法 + 工厂 + pullEvents，
 * 状态机/解析逻辑内聚。LeadStatusMachine 删除。
 *
 * <p><b>状态机</b>（合法转移穷举，对齐 API.md §3.10）：
 * <pre>
 * NEW ──▶ ASSIGNED ──▶ CONTACTING ──▶ CONVERTED
 *  │        │  │          │
 *  │        │  └▶ LOST     ├─▶ LOST
 *  │        └──────────────┘
 *  └─▶ INVALID         └─▶ INVALID
 * </pre>
 * CONVERTED / INVALID / LOST 为终态。同状态请求幂等放行。
 *
 * <p><b>transit 副作用</b>（封装在聚合根）：
 * <ul>
 *   <li>→ CONVERTED：设 convertedAt = now</li>
 *   <li>→ ASSIGNED：actorId 非 null 时设 assigneeId = actorId</li>
 *   <li>→ CONVERTED 发 LeadConvertedEvent（Analytics 归因消费）</li>
 * </ul>
 *
 * @see Lead
 */
public final class LeadAggregate {

    /** 线索状态枚举（DB 存 lowercase）。 */
    public enum Status { NEW, ASSIGNED, CONTACTING, CONVERTED, INVALID, LOST }

    private static final Map<Status, Set<Status>> TRANSITIONS = Map.of(
            Status.NEW, EnumSet.of(Status.ASSIGNED, Status.INVALID),
            Status.ASSIGNED, EnumSet.of(Status.CONTACTING, Status.INVALID, Status.LOST),
            Status.CONTACTING, EnumSet.of(Status.CONVERTED, Status.LOST, Status.INVALID),
            Status.CONVERTED, EnumSet.noneOf(Status.class),
            Status.INVALID, EnumSet.noneOf(Status.class),
            Status.LOST, EnumSet.noneOf(Status.class)
    );

    private final Lead root;
    private final List<DomainEvent> events = new ArrayList<>();

    private LeadAggregate(Lead root) {
        this.root = root;
    }

    /** 工厂：从持久化重建。 */
    public static LeadAggregate reconstitute(Lead persisted) {
        return new LeadAggregate(persisted);
    }

    /**
     * 工厂：从留资提交创建新线索（status = initialStatus，默认 NEW）。
     *
     * <p>聚合根统一入口，避免 Service 直接 new Lead() 绕过不变量。所有"首次创建"路径
     * 必须经此方法；持久化后字段（id/createdAt/updatedAt）在此一次性填充。
     *
     * @param id            UUID（由 Service 生成）
     * @param siteId        站点 id
     * @param formId        表单 id
     * @param pageId        落地页 id（可 null，回退表单绑定页）
     * @param contactJson   AES 加密后的联系人 JSON（Service 已加密）
     * @param utmJson       UTM 来源 JSON
     * @param dedupHash     去重哈希（Service 已计算）
     * @param sourceIp      来源 IP
     * @param visitorId     访客 id
     * @param initialStatus 初始状态（通常 NEW；MARK_DUPLICATE 策略下为 INVALID）
     */
    public static LeadAggregate newLead(String id, String siteId, String formId, String pageId,
            String contactJson, String utmJson, String dedupHash, String sourceIp, String visitorId,
            Status initialStatus) {
        Instant now = Instant.now();
        Lead lead = new Lead();
        lead.setId(id);
        lead.setSiteId(siteId);
        lead.setFormId(formId);
        lead.setPageId(pageId);
        lead.setContactJson(contactJson);
        lead.setUtmJson(utmJson);
        lead.setStatus(initialStatus.name().toLowerCase());
        lead.setDedupHash(dedupHash);
        lead.setSourceIp(sourceIp);
        lead.setVisitorId(visitorId);
        lead.setCreatedAt(now);
        lead.setUpdatedAt(now);
        return new LeadAggregate(lead);
    }

    /**
     * 状态转换（聚合根状态机 + 副作用 + 事件）。
     *
     * @param toStatusRaw 目标状态（字符串，大小写不敏感）
     * @param actorId     操作人（→ASSIGNED 时设 assigneeId；可 null）
     */
    public void transit(String toStatusRaw, String actorId) {
        Status from = parseStatusEnum(root.getStatus());
        Status to = parseStatusEnum(toStatusRaw);
        if (from == to) {
            return;   // 同状态幂等放行
        }
        if (!canTransitEnum(from, to)) {
            throw BusinessException.leadInvalidTransition(from.name(), to.name());
        }
        Instant now = Instant.now();
        // 副作用：CONVERTED 设 convertedAt；ASSIGNED 设 assigneeId
        root.setConvertedAt(to == Status.CONVERTED ? now : root.getConvertedAt());
        root.setAssigneeId(to == Status.ASSIGNED && actorId != null ? actorId : root.getAssigneeId());
        root.setStatus(to.name().toLowerCase());
        root.setUpdatedAt(now);
        // 事件：→ CONVERTED 发 LeadConvertedEvent（Analytics 归因）
        if (to == Status.CONVERTED) {
            events.add(new LeadConvertedEvent(root.getId(), root.getSiteId(), now, now));
        }
    }

    public Lead toLead() {
        return root;
    }

    public List<DomainEvent> pullEvents() {
        List<DomainEvent> drained = new ArrayList<>(events);
        events.clear();
        return drained;
    }

    // ===== 包内可见的状态机工具（仅测试与同包聚合根使用；禁止 Service 直接调用以绕过聚合根） =====

    /** 当前状态是否可转移到目标状态（字符串重载，大小写不敏感）。包内可见：测试用。 */
    static boolean canTransit(String fromRaw, String toRaw) {
        Status from = tryParse(fromRaw);
        Status to = tryParse(toRaw);
        if (from == null || to == null) return false;
        return canTransitEnum(from, to);
    }

    /**
     * 当前聚合根状态是否可转移到目标（实例重载，对外推荐入口；读取 {@code root.getStatus()}，
     * 调用方无需自行解析）。聚合根实例方法是 DDD 推荐形式，避免静态工具式"外部 check 再 mutate"反模式。
     */
    public boolean canTransit(Status to) {
        Status from = parseStatusEnum(root.getStatus());
        return canTransitEnum(from, to);
    }

    /** Status 枚举重载（聚合根内部 transit 用）。 */
    private static boolean canTransitEnum(Status from, Status to) {
        if (from == null || to == null) return false;
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(Status.class)).contains(to);
    }

    /** 字符串状态 → lowercase 规范值；非法抛 LEAD_INVALID_STATUS。null→"new"。包内可见：测试用。 */
    static String parseStatus(String raw) {
        return parseStatusEnum(raw).name().toLowerCase();
    }

    private static Status parseStatusEnum(String raw) {
        if (raw == null) return Status.NEW;
        try {
            return Status.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BusinessException.leadInvalidStatus(raw);
        }
    }

    private static Status tryParse(String raw) {
        if (raw == null) return null;
        try {
            return Status.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
