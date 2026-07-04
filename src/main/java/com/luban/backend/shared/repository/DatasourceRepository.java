package com.luban.backend.shared.repository;

import com.luban.backend.shared.domain.DatasourceAggregate;
import com.luban.backend.shared.entity.Datasource;

import java.util.List;

/**
 * 数据源仓储接口（backend-ddd-refactor plan v2 T11）。
 *
 * <p>领域抽象（domain 层依赖此接口，不感知 MyBatis）。封装 {@code DatasourceMapper}：
 * <ul>
 *   <li>聚合根加载：{@link #findById(String, String)} 支持多租户租户守卫（siteId 非空时按双键查），
 *       siteId 为空时回退 id-only（admin/internal 兼容路径）</li>
 *   <li>聚合根保存：{@link #save}（insert or update）</li>
 *   <li>删除：{@link #delete}（含租户守卫回退）</li>
 *   <li>读模型列表：{@link #listBySiteId}（读侧，返回 entity）</li>
 * </ul>
 *
 * @see DatasourceAggregate
 */
public interface DatasourceRepository {

    /**
     * 按 id 加载聚合根。
     *
     * @param id     数据源 id
     * @param siteId 站点 id（非空时按双键查做租户守卫；null/blank 时回退 id-only admin 路径）
     * @return 聚合根，不存在返回 null
     */
    DatasourceAggregate findById(String id, String siteId);

    /**
     * 按 id 加载聚合根（无租户守卫，admin 内部路径）。
     *
     * <p>用于 {@code testConnection} 等无 siteId 参数的管理端用例。
     * 调用方须确认是 admin 上下文（已由 Controller 层鉴权）。
     */
    DatasourceAggregate findByIdAdmin(String id);

    /** 列表查询（读模型，按 siteId）。 */
    List<Datasource> listBySiteId(String siteId);

    /**
     * 保存聚合根（insert or update，按 id 存在性判定）。
     * UNIQUE 冲突抛 {@link org.springframework.dao.DataIntegrityViolationException}，
     * 由 Service 翻译为 DATASOURCE_NAME_CONFLICT。
     */
    void save(DatasourceAggregate aggregate);

    /**
     * 删除（含租户守卫回退）。
     *
     * @param id     数据源 id
     * @param siteId 站点 id（非空按双键删；null/blank 回退 id-only）
     * @return 影响行数（0 表示不存在）
     */
    int delete(String id, String siteId);
}
