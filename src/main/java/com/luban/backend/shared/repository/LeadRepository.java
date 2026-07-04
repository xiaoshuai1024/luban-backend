package com.luban.backend.shared.repository;

import com.luban.backend.shared.domain.LeadAggregate;
import com.luban.backend.shared.entity.Lead;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 线索仓储接口（backend-ddd-refactor plan v2 T7）。
 *
 * <p>封装 LeadMapper 全部方法（含去重窗口查询），LeadService 零 LeadMapper 依赖。
 * 去重操作（countByFormHashInWindow 等）是跨线索窗口查询，非聚合根操作，
 * 作为读模型/基础设施查询暴露在 Repository（对齐 FormRepository 封装跨聚合 LeadMapper 的范式）。
 */
public interface LeadRepository {
    Optional<LeadAggregate> findById(String id, String siteId);
    void save(LeadAggregate agg);
    void updateStatus(String id, String siteId, String status,
                      String assigneeId, Instant convertedAt, Instant updatedAt);

    // 读模型（列表/导出/计数）
    List<Lead> listByQuery(String siteId, String status, String formId, String assigneeId, int offset, int limit);
    int countByQuery(String siteId, String status, String formId, String assigneeId);
    List<Lead> listAllForExport(String siteId);
    List<Lead> listForExport(String siteId, String status, String formId, String assigneeId);

    // 去重窗口操作（submit 编排用）
    int countByFormHashInWindow(String formId, String hash, int windowSeconds);
    Lead getByFormHashInWindow(String formId, String hash, int windowSeconds);
    void deleteByFormHashInWindow(String formId, String hash, int windowSeconds);
    void updateContact(String id, String siteId, String contactJson, String utmJson, Instant updatedAt);
}
