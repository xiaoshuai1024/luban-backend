package com.luban.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.dto.FormResponse;
import com.luban.backend.dto.FormSaveRequest;
import com.luban.backend.entity.Form;
import com.luban.backend.exception.BusinessException;
import com.luban.backend.mapper.FormMapper;
import com.luban.backend.mapper.LeadMapper;
import com.luban.backend.mapper.SiteMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Form 表单领域服务（管理端 CRUD）。
 */
@Service
public class FormService {

    private final FormMapper formMapper;
    private final SiteMapper siteMapper;
    private final LeadMapper leadMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FormService(FormMapper formMapper, SiteMapper siteMapper, LeadMapper leadMapper) {
        this.formMapper = formMapper;
        this.siteMapper = siteMapper;
        this.leadMapper = leadMapper;
    }

    public List<FormResponse> list(String siteId) {
        if (siteMapper.getById(siteId) == null) throw BusinessException.siteNotFound();
        return formMapper.listBySiteId(siteId).stream().map(FormResponse::fromEntity).collect(Collectors.toList());
    }

    public FormResponse get(String siteId, String id) {
        Form f = formMapper.getByIdAndSiteId(id, siteId);
        if (f == null) throw BusinessException.formNotFound();
        return FormResponse.fromEntity(f);
    }

    @Transactional(rollbackFor = Exception.class)
    public FormResponse create(FormSaveRequest req) {
        if (siteMapper.getById(req.siteId()) == null) throw BusinessException.siteNotFound();
        Form f = new Form();
        f.setId(UUID.randomUUID().toString());
        f.setSiteId(req.siteId());
        f.setPageId(req.pageId());
        f.setName(req.name());
        f.setFieldSchemaJson(toJson(req.fieldSchema()));
        f.setSubmitConfigJson(req.submitConfig() != null ? toJson(req.submitConfig()) : "{}");
        f.setDedupKeysJson(toJson(req.dedupKeys()));
        f.setDedupWindow(req.dedupWindow() != null ? req.dedupWindow() : 86400);
        f.setDedupPolicy(req.dedupPolicy() != null && !req.dedupPolicy().isBlank() ? req.dedupPolicy() : "reject");
        f.setAntiSpamJson(toJson(req.antiSpam()));
        f.setStatus(req.status() != null ? req.status() : "active");
        Instant now = Instant.now();
        f.setCreatedAt(now);
        f.setUpdatedAt(now);
        formMapper.insert(f);
        return FormResponse.fromEntity(f);
    }

    @Transactional(rollbackFor = Exception.class)
    public FormResponse update(String siteId, String id, FormSaveRequest req) {
        Form existing = formMapper.getByIdAndSiteId(id, siteId);
        if (existing == null) throw BusinessException.formNotFound();
        existing.setName(req.name() != null ? req.name() : existing.getName());
        existing.setFieldSchemaJson(req.fieldSchema() != null ? toJson(req.fieldSchema()) : existing.getFieldSchemaJson());
        existing.setSubmitConfigJson(req.submitConfig() != null ? toJson(req.submitConfig()) : existing.getSubmitConfigJson());
        existing.setDedupKeysJson(req.dedupKeys() != null ? toJson(req.dedupKeys()) : existing.getDedupKeysJson());
        existing.setDedupWindow(req.dedupWindow() != null ? req.dedupWindow() : existing.getDedupWindow());
        existing.setDedupPolicy(req.dedupPolicy() != null && !req.dedupPolicy().isBlank() ? req.dedupPolicy() : existing.getDedupPolicy());
        existing.setAntiSpamJson(req.antiSpam() != null ? toJson(req.antiSpam()) : existing.getAntiSpamJson());
        existing.setStatus(req.status() != null ? req.status() : existing.getStatus());
        existing.setUpdatedAt(Instant.now());
        formMapper.update(existing);
        return FormResponse.fromEntity(existing);
    }

    /**
     * 删除表单：先校验存在，再校验无线索（有线索则 409 拒绝，防误删数据）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String siteId, String id) {
        Form existing = formMapper.getByIdAndSiteId(id, siteId);
        if (existing == null) throw BusinessException.formNotFound();
        if (leadMapper.countByFormId(id) > 0) throw BusinessException.formHasLeads();
        formMapper.deleteById(id);
    }

    private String toJson(JsonNode node) {
        if (node == null) return null;
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }
}
