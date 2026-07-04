package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.domain.LeadAggregate;
import com.luban.backend.shared.entity.Lead;
import com.luban.backend.shared.mapper.LeadMapper;
import com.luban.backend.shared.repository.LeadRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 线索仓储实现（backend-ddd-refactor plan v2 T7）。
 * 封装 LeadMapper 全部方法。save 仅 insert（Lead 无通用 update，transitStatus 走 updateStatus）。
 */
@Repository
public class LeadRepositoryImpl implements LeadRepository {

    private final LeadMapper leadMapper;

    public LeadRepositoryImpl(LeadMapper leadMapper) {
        this.leadMapper = leadMapper;
    }

    @Override
    public Optional<LeadAggregate> findById(String id, String siteId) {
        Lead l = leadMapper.getByIdAndSiteId(id, siteId);
        return Optional.ofNullable(l).map(LeadAggregate::reconstitute);
    }

    @Override
    public void save(LeadAggregate agg) {
        leadMapper.insert(agg.toLead());
    }

    @Override
    public void updateStatus(String id, String siteId, String status,
                             String assigneeId, Instant convertedAt, Instant updatedAt) {
        leadMapper.updateStatus(id, siteId, status, assigneeId, convertedAt, updatedAt);
    }

    @Override
    public List<Lead> listByQuery(String siteId, String status, String formId, String assigneeId, int offset, int limit) {
        return leadMapper.listByQuery(siteId, status, formId, assigneeId, offset, limit);
    }

    @Override
    public int countByQuery(String siteId, String status, String formId, String assigneeId) {
        return leadMapper.countByQuery(siteId, status, formId, assigneeId);
    }

    @Override
    public List<Lead> listAllForExport(String siteId) {
        return leadMapper.listAllForExport(siteId);
    }

    @Override
    public List<Lead> listForExport(String siteId, String status, String formId, String assigneeId) {
        return leadMapper.listForExport(siteId, status, formId, assigneeId);
    }

    @Override
    public int countByFormHashInWindow(String formId, String hash, int windowSeconds) {
        return leadMapper.countByFormHashInWindow(formId, hash, windowSeconds);
    }

    @Override
    public Lead getByFormHashInWindow(String formId, String hash, int windowSeconds) {
        return leadMapper.getByFormHashInWindow(formId, hash, windowSeconds);
    }

    @Override
    public void deleteByFormHashInWindow(String formId, String hash, int windowSeconds) {
        leadMapper.deleteByFormHashInWindow(formId, hash, windowSeconds);
    }

    @Override
    public void updateContact(String id, String siteId, String contactJson, String utmJson, Instant updatedAt) {
        leadMapper.updateContact(id, siteId, contactJson, utmJson, updatedAt);
    }
}
