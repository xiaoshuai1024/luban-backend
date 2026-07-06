package com.luban.backend.operatorside.service;
import com.luban.backend.shared.util.JsonUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.shared.domain.FormAggregate;
import com.luban.backend.shared.dto.FormResponse;
import com.luban.backend.shared.dto.FormSaveRequest;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.repository.FormRepository;
import com.luban.backend.shared.repository.SiteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 表单应用服务（backend-ddd-refactor plan v2 T8）。
 *
 * <p>用例编排：加载聚合根 → 调聚合根方法 → 保存。
 * 业务不变量（dedupPolicy/status 白名单、有线索不可删）下沉到 {@link FormAggregate}。
 *
 * <p>持久化经 {@link FormRepository}（不直接依赖 FormMapper/LeadMapper，ArchUnit 守护）。
 * JSON 序列化（JsonNode→String）属本服务 infra，序列化后传聚合根。
 * SITE_NOT_FOUND 跨聚合种子校验保留在 Service（经 SiteRepository.existsById 校验站点存在）。
 */
@Service
public class FormService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FormService.class);

    private final FormRepository formRepository;
    private final SiteRepository siteRepository;


    public FormService(FormRepository formRepository, SiteRepository siteRepository) {
        this.formRepository = formRepository;
        this.siteRepository = siteRepository;
    }

    public List<FormResponse> list(String siteId) {
        if (!siteRepository.existsById(siteId)) { log.warn("站点不存在 siteId={}（疑似越权）", siteId); throw BusinessException.siteNotFound(); }
        return formRepository.listBySiteId(siteId).stream()
                .map(FormResponse::fromEntity).collect(Collectors.toList());
    }

    public FormResponse get(String siteId, String id) {
        FormAggregate agg = formRepository.findById(id, siteId);
        if (agg == null) throw BusinessException.formNotFound();
        return FormResponse.fromEntity(agg.toEntity());
    }

    @Transactional(rollbackFor = Exception.class)
    public FormResponse create(FormSaveRequest req) {
        if (!siteRepository.existsById(req.siteId())) {
            log.warn("站点不存在 siteId={}（疑似越权）", req.siteId());
            throw BusinessException.siteNotFound();
        }
        FormAggregate agg = FormAggregate.newForm(
                UUID.randomUUID().toString(), req.siteId(), req.pageId(), req.name(),
                toJson(req.fieldSchema()),
                req.submitConfig() != null ? toJson(req.submitConfig()) : "{}",
                toJson(req.dedupKeys()),
                req.dedupWindow(), req.dedupPolicy(),
                toJson(req.antiSpam()), req.status());
        formRepository.save(agg);
        return FormResponse.fromEntity(agg.toEntity());
    }

    @Transactional(rollbackFor = Exception.class)
    public FormResponse update(String siteId, String id, FormSaveRequest req) {
        FormAggregate agg = formRepository.findById(id, siteId);
        if (agg == null) throw BusinessException.formNotFound();
        agg.update(req.name(),
                req.fieldSchema() != null ? toJson(req.fieldSchema()) : null,
                req.submitConfig() != null ? toJson(req.submitConfig()) : null,
                req.dedupKeys() != null ? toJson(req.dedupKeys()) : null,
                req.dedupWindow(), req.dedupPolicy(),
                req.antiSpam() != null ? toJson(req.antiSpam()) : null,
                req.status());
        formRepository.save(agg);
        return FormResponse.fromEntity(agg.toEntity());
    }

    /**
     * 删除表单：先校验存在，再校验无线索（聚合根断言决策），最后删除。
     * 跨聚合线索计数经 Repository 封装，Service 不直接依赖 LeadMapper。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String siteId, String id) {
        FormAggregate agg = formRepository.findById(id, siteId);
        if (agg == null) throw BusinessException.formNotFound();
        agg.assertDeletable(formRepository.countLeadsByFormId(id) > 0);
        formRepository.deleteById(id);
    }

    private String toJson(JsonNode node) {
        if (node == null) return null;
        try {
            return JsonUtil.MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }
}
