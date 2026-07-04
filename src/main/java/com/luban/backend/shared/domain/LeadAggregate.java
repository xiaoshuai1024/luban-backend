package com.luban.backend.shared.domain;

import com.luban.backend.shared.domain.event.DomainEvent;
import com.luban.backend.shared.domain.event.LeadConvertedEvent;
import com.luban.backend.shared.entity.Lead;
import com.luban.backend.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

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
            throw new BusinessException(HttpStatus.CONFLICT, "LEAD_INVALID_TRANSITION",
                    "非法状态转移: " + from + " -> " + to);
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

    // ===== 无状态静态工具（状态机 + 解析，从 LeadStatusMachine 迁移） =====

    /** 当前状态是否可转移到目标状态（字符串重载，大小写不敏感）。 */
    public static boolean canTransit(String fromRaw, String toRaw) {
        Status from = tryParse(fromRaw);
        Status to = tryParse(toRaw);
        if (from == null || to == null) return false;
        return canTransitEnum(from, to);
    }

    /** Status 枚举重载（聚合根内部 transit 用）。 */
    private static boolean canTransitEnum(Status from, Status to) {
        if (from == null || to == null) return false;
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(Status.class)).contains(to);
    }

    /** 字符串状态 → lowercase 规范值；非法抛 LEAD_INVALID_STATUS。null→"new"。 */
    public static String parseStatus(String raw) {
        return parseStatusEnum(raw).name().toLowerCase();
    }

    private static Status parseStatusEnum(String raw) {
        if (raw == null) return Status.NEW;
        try {
            return Status.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LEAD_INVALID_STATUS",
                    "未知线索状态: " + raw);
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
