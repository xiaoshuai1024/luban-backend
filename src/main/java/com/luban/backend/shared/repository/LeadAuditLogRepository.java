package com.luban.backend.shared.repository;

import com.luban.backend.shared.entity.LeadAuditLog;

/**
 * LeadAuditLog 仓储接口（domain 抽象，不依赖 MyBatis）。
 *
 * <p>留资审计日志的追加写（append-only），用于留资状态变更追踪。
 */
public interface LeadAuditLogRepository {

    void insert(LeadAuditLog log);
}
