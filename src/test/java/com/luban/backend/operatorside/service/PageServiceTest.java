package com.luban.backend.operatorside.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.operatorside.repository.PublishedPageProjection;
import com.luban.backend.shared.domain.PageAggregate;
import com.luban.backend.shared.dto.PageResponse;
import com.luban.backend.shared.entity.Page;
import com.luban.backend.shared.entity.Site;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.repository.PageRepository;
import com.luban.backend.shared.repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PageService 单测（backend-ddd-refactor plan v2 T6，补覆盖率；G1 重构后）。
 *
 * <p>覆盖关键路径：create / update（含 PUT→published 双写一致性）/ publish / unpublish / delete /
 * get / list / 状态机守护 / siteNotFound 守卫。
 * v2 G1：published_pages 投影抽到 {@link PublishedPageProjection}，本测 mock 投影接口而非原始 Mapper。
 */
@ExtendWith(MockitoExtension.class)
class PageServiceTest {

    @Mock private PageRepository pageRepository;
    @Mock private PublishedPageProjection publishedPageProjection;
    @Mock private SiteRepository siteRepository;
    @Mock private PageVersionService versionService;

    private PageService service;

    @BeforeEach
    void setUp() {
        service = new PageService(pageRepository, publishedPageProjection, siteRepository, versionService);
    }

    private Site sampleSite() {
        Site s = new Site();
        s.setId("site-1");
        s.setName("测试站点");
        return s;
    }

    private Page draftPage() {
        Page p = new Page();
        p.setId("page-1");
        p.setSiteId("site-1");
        p.setName("首页");
        p.setPath("/home");
        p.setStatus("draft");
        p.setSchemaJson("{}");
        p.setCreatedAt(java.time.Instant.now());
        p.setUpdatedAt(java.time.Instant.now());
        return p;
    }

    // ===== list / get =====

    @Test
    void list_throws_when_siteNotFound() {
        when(siteRepository.existsById("ghost")).thenReturn(false);

        assertThatThrownBy(() -> service.list("ghost"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("SITE_NOT_FOUND");
    }

    @Test
    void get_throws_when_pageNotFound() {
        when(pageRepository.findById("page-x", "site-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("site-1", "page-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("PAGE_NOT_FOUND");
    }

    // ===== create =====

    @Test
    void create_persists_draft_page_and_snapshot() throws Exception {
        when(siteRepository.existsById("site-1")).thenReturn(true);

        PageResponse resp = service.create("site-1", "首页", "/home", null,
                new ObjectMapper().readTree("{\"x\":1}"), null);

        assertThat(resp.path()).isEqualTo("/home");
        assertThat(resp.status()).isEqualTo("draft");
        verify(pageRepository).save(any());
        verify(versionService).createSnapshot(anyString(), any(), eq("初始版本"), eq(null));
    }

    @Test
    void create_throws_when_siteNotFound() {
        when(siteRepository.existsById("ghost")).thenReturn(false);

        assertThatThrownBy(() -> service.create("ghost", "n", "/p", null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("SITE_NOT_FOUND");
        verify(pageRepository, never()).save(any());
    }

    @Test
    void create_with_published_status_uses_aggregate_state_machine() throws Exception {
        when(siteRepository.existsById("site-1")).thenReturn(true);

        // status=published 经聚合根状态机（draft→published 允许）
        service.create("site-1", "首页", "/home", "published",
                new ObjectMapper().readTree("{}"), null);

        ArgumentCaptor<PageAggregate> captor = ArgumentCaptor.forClass(PageAggregate.class);
        verify(pageRepository).save(captor.capture());
        // 注意：create 路径仅 setStatus（未走聚合根 publish），故 publishedAt 仍为 null
        assertThat(captor.getValue().toPage().getStatus()).isEqualTo("published");
    }

    @Test
    void create_throws_on_invalid_status() {
        when(siteRepository.existsById("site-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create("site-1", "n", "/p", "bogus", null, null))
                .isInstanceOf(BusinessException.class);
    }

    // ===== update + 双写一致性 =====

    @Test
    void update_to_published_writes_published_snapshot() {
        when(pageRepository.findById("page-1", "site-1"))
                .thenReturn(Optional.of(PageAggregate.reconstitute(draftPage())));

        PageResponse resp = service.update("site-1", "page-1", "新名", "/home", "published", null, null);

        assertThat(resp.status()).isEqualTo("published");
        // 双写一致性：投影协作者 syncOnStatusChange 被调用（内部聚合根产快照含 actor）
        verify(publishedPageProjection).syncOnStatusChange(eq("site-1"), any(PageAggregate.class), eq("draft"));
        verify(pageRepository).save(any());
    }

    @Test
    void update_unpublished_to_archived_clears_published_snapshot_no_op() {
        // draft → archived：未发布过，syncPublishedState 不触发清理
        when(pageRepository.findById("page-1", "site-1"))
                .thenReturn(Optional.of(PageAggregate.reconstitute(draftPage())));

        service.update("site-1", "page-1", "n", "/home", "archived", null, null);

        // 投影 syncOnStatusChange 仍被调用（内部判断 nowPublished/wasPublished 均为 false → 不触发 delete）
        verify(publishedPageProjection).syncOnStatusChange(eq("site-1"), any(PageAggregate.class), eq("draft"));
    }

    @Test
    void update_throws_when_not_found() {
        when(pageRepository.findById("page-x", "site-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("site-1", "page-x", "n", "/p", null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("PAGE_NOT_FOUND");
    }

    // ===== publish / unpublish =====

    @Test
    void publish_writes_snapshot_and_updates_publish_status() throws Exception {
        Page p = draftPage();
        when(pageRepository.findById("page-1", "site-1"))
                .thenReturn(Optional.of(PageAggregate.reconstitute(p)));

        PageResponse resp = service.publish("site-1", "page-1", "user-9");

        assertThat(resp.status()).isEqualTo("published");
        verify(publishedPageProjection).upsertOnPublish(any(PageAggregate.class));
        verify(pageRepository).updatePublishStatus(eq("page-1"), eq("site-1"), eq("published"),
                any(), eq("user-9"), any());
        verify(versionService).createSnapshot(eq("page-1"), any(), eq("发布"), eq("user-9"));
    }

    @Test
    void publish_is_idempotent_for_already_published() {
        // 已 published 的页面再 publish：聚合根 no-op（不发事件、不改 publishedAt）
        Page p = draftPage();
        p.setStatus("published");
        p.setPublishedAt(java.time.Instant.now());
        p.setPublishedBy("user-1");
        when(pageRepository.findById("page-1", "site-1"))
                .thenReturn(Optional.of(PageAggregate.reconstitute(p)));

        service.publish("site-1", "page-1", "user-9");

        // 投影 upsertOnPublish 仍被调用（覆盖式重写快照，幂等）
        verify(publishedPageProjection).upsertOnPublish(any(PageAggregate.class));
    }

    @Test
    void unpublish_clears_snapshot_and_sets_archived() {
        Page p = draftPage();
        p.setStatus("published");
        p.setPublishedAt(java.time.Instant.now());
        p.setPublishedBy("user-1");
        when(pageRepository.findById("page-1", "site-1"))
                .thenReturn(Optional.of(PageAggregate.reconstitute(p)));

        PageResponse resp = service.unpublish("site-1", "page-1");

        assertThat(resp.status()).isEqualTo("archived");
        verify(publishedPageProjection).removeByPageAndSite("page-1", "site-1");
        verify(pageRepository).updatePublishStatus(eq("page-1"), eq("site-1"), eq("archived"),
                any(), any(), any());
    }

    @Test
    void unpublish_throws_when_draft() {
        // draft 不可下线（聚合根状态机守护）
        when(pageRepository.findById("page-1", "site-1"))
                .thenReturn(Optional.of(PageAggregate.reconstitute(draftPage())));

        assertThatThrownBy(() -> service.unpublish("site-1", "page-1"))
                .isInstanceOf(BusinessException.class);
        verify(publishedPageProjection, never()).removeByPageAndSite(anyString(), anyString());
    }

    // ===== delete =====

    @Test
    void delete_clears_snapshot_and_removes_page() {
        when(pageRepository.deleteByIdAndSiteId("page-1", "site-1")).thenReturn(1);

        service.delete("site-1", "page-1");

        verify(publishedPageProjection).removeByPageAndSite("page-1", "site-1");
        verify(pageRepository).deleteByIdAndSiteId("page-1", "site-1");
    }

    @Test
    void delete_throws_when_not_found() {
        when(pageRepository.deleteByIdAndSiteId("page-x", "site-1")).thenReturn(0);

        assertThatThrownBy(() -> service.delete("site-1", "page-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("PAGE_NOT_FOUND");
    }
}
