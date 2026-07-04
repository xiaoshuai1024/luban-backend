package com.luban.backend.operatorside.service;

import com.fasterxml.jackson.databind.node.POJONode;
import com.luban.backend.shared.domain.DatasourceAggregate;
import com.luban.backend.shared.dto.DatasourceResponse;
import com.luban.backend.shared.dto.DatasourceTestResult;
import com.luban.backend.shared.entity.Datasource;
import com.luban.backend.shared.entity.Site;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.SiteMapper;
import com.luban.backend.shared.repository.DatasourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DatasourceService 单测（backend-ddd-refactor plan v2 T18，补覆盖率）。
 * contract 测试已覆盖 HTTP 路径，此处补聚合根委托路径的单测。
 */
@ExtendWith(MockitoExtension.class)
class DatasourceServiceTest {

    @Mock private DatasourceRepository datasourceRepository;
    @Mock private SiteMapper siteMapper;

    private DatasourceService service;

    @BeforeEach
    void setUp() {
        service = new DatasourceService(datasourceRepository, siteMapper);
    }

    @Test
    void list_returns_datasources_when_site_exists() {
        when(siteMapper.getById("site-1")).thenReturn(new Site());
        Datasource ds = new Datasource();
        ds.setId("ds-1");
        ds.setSiteId("site-1");
        ds.setName("列表");
        ds.setType("static");
        when(datasourceRepository.listBySiteId("site-1")).thenReturn(List.of(ds));

        List<DatasourceResponse> out = service.list("site-1");

        assertThat(out).hasSize(1);
        assertThat(out.get(0).name()).isEqualTo("列表");
    }

    @Test
    void list_throws_when_site_not_found() {
        when(siteMapper.getById("site-x")).thenReturn(null);

        assertThatThrownBy(() -> service.list("site-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("SITE_NOT_FOUND");
    }

    @Test
    void list_throws_when_siteId_blank() {
        assertThatThrownBy(() -> service.list("  "))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void get_returns_datasource() {
        Datasource ds = new Datasource();
        ds.setId("ds-1");
        ds.setType("static");
        when(datasourceRepository.findById("ds-1", "site-1"))
                .thenReturn(DatasourceAggregate.reconstitute(ds));

        DatasourceResponse resp = service.get("ds-1", "site-1");

        assertThat(resp.id()).isEqualTo("ds-1");
    }

    @Test
    void get_throws_when_not_found() {
        when(datasourceRepository.findById("ds-x", "site-1")).thenReturn(null);

        assertThatThrownBy(() -> service.get("ds-x", "site-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("DATASOURCE_NOT_FOUND");
    }

    @Test
    void create_inserts_when_site_exists() {
        when(siteMapper.getById("site-1")).thenReturn(new Site());

        DatasourceResponse resp = service.create("site-1", "新数据源", "api", null);

        assertThat(resp.name()).isEqualTo("新数据源");
        assertThat(resp.type()).isEqualTo("api");
        verify(datasourceRepository).save(any());
    }

    @Test
    void create_throws_when_site_not_found() {
        when(siteMapper.getById("site-x")).thenReturn(null);

        assertThatThrownBy(() -> service.create("site-x", "n", "static", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("SITE_NOT_FOUND");
        verify(datasourceRepository, never()).save(any());
    }

    @Test
    void create_throws_when_type_invalid() {
        when(siteMapper.getById("site-1")).thenReturn(new Site());

        assertThatThrownBy(() -> service.create("site-1", "n", "mongodb", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void update_modifies_existing() {
        Datasource ds = new Datasource();
        ds.setId("ds-1");
        ds.setSiteId("site-1");
        ds.setType("static");
        when(datasourceRepository.findById("ds-1", "site-1"))
                .thenReturn(DatasourceAggregate.reconstitute(ds));

        DatasourceResponse resp = service.update("ds-1", "site-1", "新名", "api", null);

        assertThat(resp.name()).isEqualTo("新名");
        assertThat(resp.type()).isEqualTo("api");
        verify(datasourceRepository).save(any());
    }

    @Test
    void update_throws_when_not_found() {
        when(datasourceRepository.findById("ds-x", "site-1")).thenReturn(null);

        assertThatThrownBy(() -> service.update("ds-x", "site-1", "n", "static", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("DATASOURCE_NOT_FOUND");
    }

    @Test
    void delete_succeeds() {
        when(datasourceRepository.delete("ds-1", "site-1")).thenReturn(1);

        service.delete("ds-1", "site-1");

        verify(datasourceRepository).delete("ds-1", "site-1");
    }

    @Test
    void delete_throws_when_not_found() {
        when(datasourceRepository.delete("ds-x", "site-1")).thenReturn(0);

        assertThatThrownBy(() -> service.delete("ds-x", "site-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("DATASOURCE_NOT_FOUND");
    }

    @Test
    void testConnection_static_returns_ok_immediately() {
        Datasource ds = new Datasource();
        ds.setId("ds-1");
        ds.setType("static");
        when(datasourceRepository.findByIdAdmin("ds-1"))
                .thenReturn(DatasourceAggregate.reconstitute(ds));

        DatasourceTestResult result = service.testConnection("ds-1");

        assertThat(result.ok()).isTrue();
        assertThat(result.latencyMs()).isEqualTo(0L);
    }

    @Test
    void testConnection_throws_when_not_found() {
        when(datasourceRepository.findByIdAdmin("ds-x")).thenReturn(null);

        assertThatThrownBy(() -> service.testConnection("ds-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("DATASOURCE_NOT_FOUND");
    }

    @Test
    void testConnection_api_missing_url_throws() {
        Datasource ds = new Datasource();
        ds.setId("ds-1");
        ds.setType("api");
        ds.setConfigJson("{}");
        when(datasourceRepository.findByIdAdmin("ds-1"))
                .thenReturn(DatasourceAggregate.reconstitute(ds));

        assertThatThrownBy(() -> service.testConnection("ds-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("DATASOURCE_CONNECTION_FAILED");
    }

    // ===== DataIntegrityViolation 分支（isDuplicate 嗅探）=====

    @Test
    void create_throws_name_conflict_on_duplicate_violation() {
        when(siteMapper.getById("site-1")).thenReturn(new Site());
        doThrow(new DataIntegrityViolationException("Duplicate entry 'n' for key 'uk_datasources_site_name'"))
                .when(datasourceRepository).save(any());

        assertThatThrownBy(() -> service.create("site-1", "n", "api", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("DATASOURCE_NAME_CONFLICT");
    }

    @Test
    void create_rethrows_non_duplicate_data_integrity_violation() {
        when(siteMapper.getById("site-1")).thenReturn(new Site());
        doThrow(new DataIntegrityViolationException("Cannot delete or update a parent row: FK constraint fails"))
                .when(datasourceRepository).save(any());

        // non-duplicate message → 原异常重新抛出（不包装为 BusinessException）
        assertThatThrownBy(() -> service.create("site-1", "n", "api", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(BusinessException.class);
    }

    @Test
    void update_throws_name_conflict_on_duplicate() {
        Datasource ds = new Datasource();
        ds.setId("ds-1");
        ds.setSiteId("site-1");
        ds.setType("static");
        when(datasourceRepository.findById("ds-1", "site-1"))
                .thenReturn(DatasourceAggregate.reconstitute(ds));
        doThrow(new DataIntegrityViolationException("Duplicate entry 'n' for key 'uk_datasources_site_name'"))
                .when(datasourceRepository).save(any());

        assertThatThrownBy(() -> service.update("ds-1", "site-1", "n", "api", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("DATASOURCE_NAME_CONFLICT");
    }

    @Test
    void update_rethrows_non_duplicate_data_integrity_violation() {
        Datasource ds = new Datasource();
        ds.setId("ds-1");
        ds.setSiteId("site-1");
        ds.setType("static");
        when(datasourceRepository.findById("ds-1", "site-1"))
                .thenReturn(DatasourceAggregate.reconstitute(ds));
        doThrow(new DataIntegrityViolationException("FK constraint fails"))
                .when(datasourceRepository).save(any());

        assertThatThrownBy(() -> service.update("ds-1", "site-1", "n", "api", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(BusinessException.class);
    }

    // ===== configToJson strict 失败 =====

    @Test
    void create_throws_invalid_argument_when_config_unserializable() {
        when(siteMapper.getById("site-1")).thenReturn(new Site());
        // POJONode 包装不可序列化对象 → writeValueAsString 抛异常 → INVALID_ARGUMENT
        POJONode unserializable = new POJONode(new Object());

        assertThatThrownBy(() -> service.create("site-1", "n", "api", unserializable))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("INVALID_ARGUMENT");
        verify(datasourceRepository, never()).save(any());
    }

    // ===== testConnection URL 无效分支 =====

    @Test
    void testConnection_invalid_url_returns_connection_failed() {
        Datasource ds = new Datasource();
        ds.setId("ds-1");
        ds.setType("api");
        // "ht!tp://bad" scheme 含非法字符 → URI.create 抛 IllegalArgumentException → DATASOURCE_CONNECTION_FAILED
        ds.setConfigJson("{\"url\":\"ht!tp://bad\"}");
        when(datasourceRepository.findByIdAdmin("ds-1"))
                .thenReturn(DatasourceAggregate.reconstitute(ds));

        assertThatThrownBy(() -> service.testConnection("ds-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("DATASOURCE_CONNECTION_FAILED");
    }
}
