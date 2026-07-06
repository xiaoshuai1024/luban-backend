package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.entity.LeadAuditLog;
import com.luban.backend.shared.mapper.LeadAuditLogMapper;
import com.luban.backend.shared.repository.LeadAuditLogRepository;
import org.springframework.stereotype.Repository;

/**
 * LeadAuditLog 仓储实现：封装 {@link LeadAuditLogMapper}。
 * append-only 审计日志。
 */
@Repository
public class LeadAuditLogRepositoryImpl implements LeadAuditLogRepository {

    private final LeadAuditLogMapper auditLogMapper;

    public LeadAuditLogRepositoryImpl(LeadAuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    public void insert(LeadAuditLog log) {
        auditLogMapper.insert(log);
    }
}
