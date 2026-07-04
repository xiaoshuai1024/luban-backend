package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.domain.FormAggregate;
import com.luban.backend.shared.entity.Form;
import com.luban.backend.shared.mapper.FormMapper;
import com.luban.backend.shared.mapper.LeadMapper;
import com.luban.backend.shared.repository.FormRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 表单仓储实现（backend-ddd-refactor plan v2 T8）。
 *
 * <p>封装 {@link FormMapper} + 跨聚合查询 {@link LeadMapper#countByFormId}（删除前置校验）。
 * LeadMapper 是实现细节，{@link FormRepository} 接口不暴露它，FormService 零跨聚合 Mapper 依赖。
 */
@Repository
public class FormRepositoryImpl implements FormRepository {

    private final FormMapper formMapper;
    private final LeadMapper leadMapper;

    public FormRepositoryImpl(FormMapper formMapper, LeadMapper leadMapper) {
        this.formMapper = formMapper;
        this.leadMapper = leadMapper;
    }

    @Override
    public FormAggregate findById(String id, String siteId) {
        Form f = formMapper.getByIdAndSiteId(id, siteId);
        return f != null ? FormAggregate.reconstitute(f) : null;
    }

    @Override
    public List<Form> listBySiteId(String siteId) {
        return formMapper.listBySiteId(siteId);
    }

    @Override
    public List<Form> listByPageId(String pageId) {
        return formMapper.listByPageId(pageId);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void save(FormAggregate aggregate) {
        Form entity = aggregate.toEntity();
        if (formMapper.getById(entity.getId()) == null) {
            formMapper.insert(entity);
        } else {
            formMapper.update(entity);
        }
    }

    @Override
    public void deleteById(String id) {
        formMapper.deleteById(id);
    }

    @Override
    public int countLeadsByFormId(String formId) {
        return leadMapper.countByFormId(formId);
    }
}
