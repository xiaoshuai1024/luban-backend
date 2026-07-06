package com.luban.backend.shared.repository;

import com.luban.backend.shared.domain.FormAggregate;
import com.luban.backend.shared.entity.Form;

import java.util.List;

/**
 * 表单仓储接口（backend-ddd-refactor plan v2 T8）。
 *
 * <p>领域抽象。封装 {@code FormMapper}（Form 聚合）+ 跨聚合查询 {@code LeadMapper.countByFormId}
 * （删除前置校验，封装在实现层，接口不暴露 LeadMapper，Service 零 LeadMapper 依赖）。
 *
 * @see FormAggregate
 */
public interface FormRepository {

    /** 按 (id, siteId) 加载聚合根（租户守卫），不存在返回 null。 */
    FormAggregate findById(String id, String siteId);

    /**
     * 按 formId 加载表单 entity（不限定 siteId）。
     * 供公开留资提交（{@code LeadService.submit}）使用——提交入口无 siteId 上下文，
     * 需先查 form 再得 siteId。返回 entity 而非聚合根，因为提交编排需读取
     * status/dedupKeys/antiSpam 等原始配置字段。
     */
    Form findFormById(String id);

    /** 列表查询（读模型，按 siteId）。 */
    List<Form> listBySiteId(String siteId);

    /** 列表查询（读模型，按 pageId）。 */
    List<Form> listByPageId(String pageId);

    /** 保存（insert or update）。 */
    void save(FormAggregate aggregate);

    /** 按 id 删除。 */
    void deleteById(String id);

    /**
     * 按 formId 统计关联线索数（删除前置校验）。
     *
     * <p>跨聚合查询封装在此（实现层调 LeadMapper），让 FormService 零跨聚合 Mapper 依赖。
     * 返回值传给 {@link FormAggregate#assertDeletable(boolean)} 做决策断言。
     */
    int countLeadsByFormId(String formId);
}
