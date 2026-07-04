package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.domain.DatasourceAggregate;
import com.luban.backend.shared.entity.Datasource;
import com.luban.backend.shared.mapper.DatasourceMapper;
import com.luban.backend.shared.repository.DatasourceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 数据源仓储实现（backend-ddd-refactor plan v2 T11）。
 *
 * <p>封装 {@link DatasourceMapper}（MyBatis），实现 {@link DatasourceRepository}。
 * 持久化异常翻译（UNIQUE→NAME_CONFLICT）保留在 Service 层（需嗅探 message 区分冲突类型）。
 *
 * <p>多租户守卫回退：siteId 为空时用 id-only 查询（admin/internal 兼容路径，
 * 对齐原 DatasourceService 行为，{@code DatasourceContractTest} 锁定）。
 */
@Repository
public class DatasourceRepositoryImpl implements DatasourceRepository {

    private final DatasourceMapper datasourceMapper;

    public DatasourceRepositoryImpl(DatasourceMapper datasourceMapper) {
        this.datasourceMapper = datasourceMapper;
    }

    @Override
    public DatasourceAggregate findById(String id, String siteId) {
        Datasource ds = (siteId == null || siteId.isBlank())
                ? datasourceMapper.getById(id)
                : datasourceMapper.getByIdAndSiteId(id, siteId);
        return ds != null ? DatasourceAggregate.reconstitute(ds) : null;
    }

    @Override
    public DatasourceAggregate findByIdAdmin(String id) {
        Datasource ds = datasourceMapper.getById(id);
        return ds != null ? DatasourceAggregate.reconstitute(ds) : null;
    }

    @Override
    public List<Datasource> listBySiteId(String siteId) {
        return datasourceMapper.listBySiteId(siteId);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void save(DatasourceAggregate aggregate) {
        Datasource entity = aggregate.toEntity();
        if (datasourceMapper.getById(entity.getId()) == null) {
            datasourceMapper.insert(entity);
        } else {
            int n = datasourceMapper.update(entity);
            if (n == 0) {
                // update 的 WHERE 含 site_id，跨站点更新返回 0 → Service 翻译为 NOT_FOUND
                throw new IllegalStateException("datasource update affected 0 rows: id=" + entity.getId());
            }
        }
    }

    @Override
    public int delete(String id, String siteId) {
        return (siteId == null || siteId.isBlank())
                ? datasourceMapper.deleteById(id)
                : datasourceMapper.deleteByIdAndSiteId(id, siteId);
    }
}
