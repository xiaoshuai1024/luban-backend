package com.luban.backend.service;

import com.luban.backend.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lead 状态机（纯逻辑，便于单测）。合法转移见 plan §3.1（对齐 API.md §3.10）：
 *
 * <pre>
 * NEW ──▶ ASSIGNED ──▶ CONTACTING ──▶ CONVERTED
 *  │        │  │          │
 *  │        │  └▶ LOST     ├─▶ LOST
 *  │        └──────────────┘
 *  └─▶ INVALID         └─▶ INVALID
 * </pre>
 *
 * 合法转移穷举（对齐 API.md §3.10）：
 * <ul>
 *   <li>NEW → ASSIGNED / INVALID</li>
 *   <li>ASSIGNED → CONTACTING / INVALID / LOST</li>
 *   <li>CONTACTING → CONVERTED / LOST / INVALID</li>
 * </ul>
 *
 * CONVERTED / INVALID / LOST 为终态。同状态请求视为幂等放行。
 */
@Service
public class LeadStatusMachine {

    public enum Status { NEW, ASSIGNED, CONTACTING, CONVERTED, INVALID, LOST }

    private static final Map<Status, Set<Status>> TRANSITIONS = Map.of(
            Status.NEW, EnumSet.of(Status.ASSIGNED, Status.INVALID),
            Status.ASSIGNED, EnumSet.of(Status.CONTACTING, Status.INVALID, Status.LOST),
            Status.CONTACTING, EnumSet.of(Status.CONVERTED, Status.LOST, Status.INVALID),
            Status.CONVERTED, EnumSet.noneOf(Status.class),
            Status.INVALID, EnumSet.noneOf(Status.class),
            Status.LOST, EnumSet.noneOf(Status.class)
    );

    /** 当前状态是否可转移到目标状态。 */
    public boolean canTransit(Status from, Status to) {
        if (from == null || to == null) return false;
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(Status.class)).contains(to);
    }

    /** 校验转移合法性；非法抛 LEAD_INVALID_TRANSITION。同状态视为幂等，放行。 */
    public void ensureValid(Status from, Status to) {
        if (from == to) return;
        if (!canTransit(from, to)) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEAD_INVALID_TRANSITION",
                    "非法状态转移: " + from + " -> " + to);
        }
    }

    /** 字符串状态转枚举；非法抛 LEAD_INVALID_STATUS。 */
    public Status parse(String raw) {
        if (raw == null) return Status.NEW;
        try {
            return Status.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LEAD_INVALID_STATUS",
                    "未知线索状态: " + raw);
        }
    }
}
