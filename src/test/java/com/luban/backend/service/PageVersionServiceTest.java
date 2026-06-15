package com.luban.backend.service;

import com.luban.backend.entity.Page;
import com.luban.backend.entity.PageVersion;
import com.luban.backend.exception.BusinessException;
import com.luban.backend.mapper.PageMapper;
import com.luban.backend.mapper.PageVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PageVersionService 单测（T-be-6）：建版本自增、列表、详情、回滚复制+建新版本、版本不存在。
 */
@ExtendWith(MockitoExtension.class)
class PageVersionServiceTest {

    @Mock private PageVersionMapper pageVersionMapper;
    @Mock private PageMapper pageMapper;

    private PageVersionService service;

    @BeforeEach
    void setup() {
        service = new PageVersionService(pageVersionMapper, pageMapper);
    }

    private Page samplePage() {
        Page p = new Page();
        p.setId("page-1");
        p.setSiteId("site-1");
        p.setName("首页");
        p.setPath("/home");
        p.setStatus("published");
        p.setSchemaJson("{\"root\":{\"id\":\"root\"}}");
        return p;
    }

    @Test
    void createVersionIncrementsVersionNumber() {
        when(pageVersionMapper.maxVersion("site-1", "page-1")).thenReturn(2);

        var resp = service.createVersion("site-1", "page-1", "{\"x\":1}", "user-1");

        assertThat(resp.version()).isEqualTo(3);
        ArgumentCaptor<PageVersion> captor = ArgumentCaptor.forClass(PageVersion.class);
        verify(pageVersionMapper).insert(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(3);
        assertThat(captor.getValue().getSchemaJson()).isEqualTo("{\"x\":1}");
        assertThat(captor.getValue().getOperatorId()).isEqualTo("user-1");
    }

    @Test
    void createVersionFromZeroWhenNoHistory() {
        when(pageVersionMapper.maxVersion("site-1", "page-1")).thenReturn(0);

        var resp = service.createVersion("site-1", "page-1", "{}", null);

        assertThat(resp.version()).isEqualTo(1);
    }

    @Test
    void listReturnsVersionsDescending() {
        when(pageMapper.getByIdAndSiteId("page-1", "site-1")).thenReturn(samplePage());
        PageVersion v2 = new PageVersion();
        v2.setVersion(2);
        PageVersion v1 = new PageVersion();
        v1.setVersion(1);
        when(pageVersionMapper.listByPageId("site-1", "page-1")).thenReturn(List.of(v2, v1));

        var list = service.list("site-1", "page-1");

        assertThat(list).hasSize(2);
        assertThat(list.get(0).version()).isEqualTo(2);
    }

    @Test
    void listRejectsMissingPage() {
        when(pageMapper.getByIdAndSiteId("ghost", "site-1")).thenReturn(null);

        assertThatThrownBy(() -> service.list("site-1", "ghost"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("PAGE_NOT_FOUND");
    }

    @Test
    void getReturnsVersionDetail() {
        when(pageMapper.getByIdAndSiteId("page-1", "site-1")).thenReturn(samplePage());
        PageVersion v = new PageVersion();
        v.setVersion(1);
        v.setSchemaJson("{\"v\":1}");
        when(pageVersionMapper.getByPageIdAndVersion("site-1", "page-1", 1)).thenReturn(v);

        var resp = service.get("site-1", "page-1", 1);

        assertThat(resp.version()).isEqualTo(1);
    }

    @Test
    void getThrowsWhenVersionMissing() {
        when(pageMapper.getByIdAndSiteId("page-1", "site-1")).thenReturn(samplePage());
        when(pageVersionMapper.getByPageIdAndVersion("site-1", "page-1", 99)).thenReturn(null);

        assertThatThrownBy(() -> service.get("site-1", "page-1", 99))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("PAGE_VERSION_NOT_FOUND");
    }

    @Test
    void rollbackCopiesTargetSchemaAndCreatesNewVersion() {
        Page page = samplePage();
        when(pageMapper.getByIdAndSiteId("page-1", "site-1")).thenReturn(page);
        PageVersion target = new PageVersion();
        target.setVersion(1);
        target.setSchemaJson("{\"rolled\":true}");
        when(pageVersionMapper.getByPageIdAndVersion("site-1", "page-1", 1)).thenReturn(target);
        when(pageVersionMapper.maxVersion("site-1", "page-1")).thenReturn(1);

        var resp = service.rollback("site-1", "page-1", 1, "user-1");

        // 1. 目标版本 schema 被写回当前页面
        verify(pageMapper).update(any(Page.class));
        assertThat(page.getSchemaJson()).isEqualTo("{\"rolled\":true}");
        // 2. 以回滚后的 schema 建一个新版本（版本号 = maxVersion + 1 = 2）
        assertThat(resp.version()).isEqualTo(2);
        ArgumentCaptor<PageVersion> captor = ArgumentCaptor.forClass(PageVersion.class);
        verify(pageVersionMapper).insert(captor.capture());
        assertThat(captor.getValue().getSchemaJson()).isEqualTo("{\"rolled\":true}");
        assertThat(captor.getValue().getOperatorId()).isEqualTo("user-1");
    }

    @Test
    void rollbackThrowsWhenTargetVersionMissing() {
        when(pageMapper.getByIdAndSiteId("page-1", "site-1")).thenReturn(samplePage());
        when(pageVersionMapper.getByPageIdAndVersion("site-1", "page-1", 99)).thenReturn(null);

        assertThatThrownBy(() -> service.rollback("site-1", "page-1", 99, "user-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("PAGE_VERSION_NOT_FOUND");
        verify(pageMapper, never()).update(any());
        verify(pageVersionMapper, never()).insert(any());
    }
}
