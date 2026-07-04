package com.luban.backend.shared.domain;

import com.luban.backend.shared.entity.Datasource;
import com.luban.backend.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DatasourceAggregate 单测（backend-ddd-refactor plan v2 T11）。
 *
 * <p>锁定真聚合根范式不变量：
 * <ul>
 *   <li>type 白名单：{@code static} / {@code api}（非法值抛 INVALID_ARGUMENT）</li>
 *   <li>工厂 newDatasource：创建初始实体（type/name/configJson）</li>
 *   <li>工厂 reconstitute：从持久化重建</li>
 *   <li>update：修改 name/type/configJson（type 须过白名单）</li>
 * </ul>
 *
 * <p>HTTP testConnection 属 infra，不进聚合根（聚合根零网络/IO 依赖）。
 * configJson 序列化（JsonNode→String）属 Service infra，聚合根只接收已序列化字符串。
 */
class DatasourceAggregateTest {

    @Test
    void newDatasourceCreatesWithAllowedType() {
        Instant before = Instant.now();
        DatasourceAggregate agg = DatasourceAggregate.newDatasource(
                "ds-1", "site-1", "用户列表", "static", "{\"rows\":[]}");

        Datasource entity = agg.toEntity();
        assertThat(entity.getId()).isEqualTo("ds-1");
        assertThat(entity.getSiteId()).isEqualTo("site-1");
        assertThat(entity.getName()).isEqualTo("用户列表");
        assertThat(entity.getType()).isEqualTo("static");
        assertThat(entity.getConfigJson()).isEqualTo("{\"rows\":[]}");
        assertThat(entity.getCreatedAt()).isBetween(before, Instant.now().plusSeconds(1));
        assertThat(entity.getUpdatedAt()).isEqualTo(entity.getCreatedAt());
    }

    @Test
    void newDatasourceAcceptsApiType() {
        DatasourceAggregate agg = DatasourceAggregate.newDatasource(
                "ds-2", "site-1", "远程API", "api", "{\"url\":\"https://x\"}");
        assertThat(agg.toEntity().getType()).isEqualTo("api");
    }

    @Test
    void newDatasourceRejectsNullType() {
        assertThatThrownBy(() -> DatasourceAggregate.newDatasource(
                "ds-3", "site-1", "x", null, "{}"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void newDatasourceRejectsUnknownType() {
        assertThatThrownBy(() -> DatasourceAggregate.newDatasource(
                "ds-4", "site-1", "x", "mongodb", "{}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("static");
    }

    @Test
    void reconstitutePreservesPersistedState() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Datasource persisted = new Datasource();
        persisted.setId("ds-9");
        persisted.setSiteId("site-1");
        persisted.setName("旧数据源");
        persisted.setType("api");
        persisted.setConfigJson("{\"url\":\"https://old\"}");
        persisted.setCreatedAt(created);
        persisted.setUpdatedAt(created);

        DatasourceAggregate agg = DatasourceAggregate.reconstitute(persisted);

        Datasource entity = agg.toEntity();
        assertThat(entity.getId()).isEqualTo("ds-9");
        assertThat(entity.getType()).isEqualTo("api");
        assertThat(entity.getName()).isEqualTo("旧数据源");
        assertThat(entity.getCreatedAt()).isEqualTo(created);
        assertThat(agg.pullEvents()).isEmpty();
    }

    @Test
    void updateModifiesFieldsWithAllowedType() {
        DatasourceAggregate agg = DatasourceAggregate.newDatasource(
                "ds-1", "site-1", "原名", "static", "{}");
        Instant originalUpdatedAt = agg.toEntity().getUpdatedAt();

        agg.update("新名", "api", "{\"url\":\"https://y\"}");

        Datasource entity = agg.toEntity();
        assertThat(entity.getName()).isEqualTo("新名");
        assertThat(entity.getType()).isEqualTo("api");
        assertThat(entity.getConfigJson()).isEqualTo("{\"url\":\"https://y\"}");
        assertThat(entity.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
        // siteId 不可变（聚合根不暴露修改 siteId 的方法，update 不改 siteId）
        assertThat(entity.getSiteId()).isEqualTo("site-1");
    }

    @Test
    void updateRejectsInvalidType() {
        DatasourceAggregate agg = DatasourceAggregate.newDatasource(
                "ds-1", "site-1", "名", "static", "{}");

        assertThatThrownBy(() -> agg.update("名", "redis", "{}"))
                .isInstanceOf(BusinessException.class);

        // 抛异常后聚合根状态不被修改
        assertThat(agg.toEntity().getType()).isEqualTo("static");
        assertThat(agg.toEntity().getName()).isEqualTo("名");
    }

    @Test
    void updateRejectsNullType() {
        DatasourceAggregate agg = DatasourceAggregate.newDatasource(
                "ds-1", "site-1", "名", "static", "{}");

        assertThatThrownBy(() -> agg.update("名", null, "{}"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void isStaticTypeHelperReflectsEntityType() {
        DatasourceAggregate stat = DatasourceAggregate.newDatasource(
                "ds-1", "site-1", "s", "static", "{}");
        DatasourceAggregate api = DatasourceAggregate.newDatasource(
                "ds-2", "site-1", "a", "api", "{}");

        assertThat(stat.isStaticType()).isTrue();
        assertThat(api.isStaticType()).isFalse();
    }

    @Test
    void parsedConfigUrlExtractsUrlForApiProbe() {
        // 聚合根提供 configJson 字符串访问（Service testConnection 用，避免重复解析逻辑散落）
        DatasourceAggregate agg = DatasourceAggregate.newDatasource(
                "ds-1", "site-1", "a", "api", "{\"url\":\"https://probe.example.com/x\"}");

        assertThat(agg.getConfigJson()).isEqualTo("{\"url\":\"https://probe.example.com/x\"}");
    }
}
