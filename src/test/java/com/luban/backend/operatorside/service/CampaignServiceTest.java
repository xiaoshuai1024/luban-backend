package com.luban.backend.operatorside.service;

import com.luban.backend.shared.domain.CampaignAggregate;
import com.luban.backend.shared.dto.CampaignResponse;
import com.luban.backend.shared.dto.CampaignSaveRequest;
import com.luban.backend.shared.entity.Campaign;
import com.luban.backend.shared.entity.Site;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.SiteMapper;
import com.luban.backend.shared.repository.CampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * CampaignService 单测（backend-ddd-refactor plan v2，改造后对齐 Repository 委托）。
 *
 * <p>原直接断言 CampaignMapper/ChannelMapper 的写法，已改为断言 CampaignRepository；
 * 跨聚合 channel 查询经 repo.hasChannels 封装，Service 不直接依赖 ChannelMapper。
 */
@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private SiteMapper siteMapper;
    @Mock private TenantGuardService tenantGuard;

    private CampaignService service;

    @BeforeEach
    void setUp() {
        service = new CampaignService(campaignRepository, siteMapper, tenantGuard);
    }

    private void stubSiteOk(String siteId) {
        when(siteMapper.getById(siteId)).thenReturn(new Site());
        doNothing().when(tenantGuard).ensureSiteAccess(siteId);
    }

    private static Instant t(String iso) {
        return Instant.parse(iso);
    }

    @Test
    void list_returns_all_campaigns() {
        stubSiteOk("site-1");
        Campaign c = new Campaign();
        c.setId("c-1");
        c.setSiteId("site-1");
        c.setName("Summer");
        c.setStatus("planned");
        when(campaignRepository.listBySiteId("site-1")).thenReturn(List.of(c));

        List<CampaignResponse> out = service.list("site-1");

        assertThat(out).hasSize(1);
        assertThat(out.get(0).name()).isEqualTo("Summer");
    }

    @Test
    void list_site_not_found_throws() {
        when(siteMapper.getById("site-x")).thenReturn(null);

        assertThatThrownBy(() -> service.list("site-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("SITE_NOT_FOUND");
        verifyNoInteractions(campaignRepository);
    }

    @Test
    void get_returns_campaign() {
        stubSiteOk("site-1");
        Campaign c = new Campaign();
        c.setId("c-1");
        c.setSiteId("site-1");
        c.setName("Summer");
        c.setStatus("active");
        when(campaignRepository.findById("c-1", "site-1")).thenReturn(Optional.of(CampaignAggregate.reconstitute(c, null)));

        CampaignResponse resp = service.get("site-1", "c-1");

        assertThat(resp.name()).isEqualTo("Summer");
        assertThat(resp.status()).isEqualTo("active");
    }

    @Test
    void get_not_found_throws() {
        stubSiteOk("site-1");
        when(campaignRepository.findById("c-x", "site-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("site-1", "c-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("CAMPAIGN_NOT_FOUND");
    }

    @Test
    void create_inserts_new_campaign_with_planned_status() {
        stubSiteOk("site-1");
        CampaignSaveRequest req = new CampaignSaveRequest(
                "site-1", "Summer Sale", t("2026-08-01T00:00:00Z"), t("2026-08-31T00:00:00Z"), null);

        CampaignResponse resp = service.create(req);

        assertThat(resp.name()).isEqualTo("Summer Sale");
        assertThat(resp.status()).isEqualTo("planned");
        ArgumentCaptor<CampaignAggregate> captor = ArgumentCaptor.forClass(CampaignAggregate.class);
        verify(campaignRepository).save(captor.capture());
        assertThat(captor.getValue().toCampaign().getStatus()).isEqualTo("planned");
        assertThat(captor.getValue().toCampaign().getId()).isNotBlank();
    }

    @Test
    void create_invalid_time_window_throws() {
        stubSiteOk("site-1");
        CampaignSaveRequest req = new CampaignSaveRequest(
                "site-1", "Bad", t("2026-08-31T00:00:00Z"), t("2026-08-01T00:00:00Z"), null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("INVALID_TIME_WINDOW");
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void create_blank_name_throws_missing_field() {
        stubSiteOk("site-1");
        CampaignSaveRequest req = new CampaignSaveRequest("site-1", " ", null, null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("MISSING_FIELD");
    }

    @Test
    void update_applies_patch_and_legal_transition() {
        stubSiteOk("site-1");
        Campaign existing = new Campaign();
        existing.setId("c-1");
        existing.setSiteId("site-1");
        existing.setName("Old");
        existing.setStatus("planned");
        when(campaignRepository.findById("c-1", "site-1")).thenReturn(Optional.of(CampaignAggregate.reconstitute(existing, null)));
        CampaignSaveRequest req = new CampaignSaveRequest(
                "site-1", "New Name", null, null, "active");

        CampaignResponse resp = service.update("site-1", "c-1", req);

        assertThat(resp.name()).isEqualTo("New Name");
        assertThat(resp.status()).isEqualTo("active");
        verify(campaignRepository).save(any(CampaignAggregate.class));
    }

    @Test
    void update_illegal_transition_throws() {
        stubSiteOk("site-1");
        Campaign existing = new Campaign();
        existing.setId("c-1");
        existing.setSiteId("site-1");
        existing.setName("X");
        existing.setStatus("planned");
        when(campaignRepository.findById("c-1", "site-1")).thenReturn(Optional.of(CampaignAggregate.reconstitute(existing, null)));
        CampaignSaveRequest req = new CampaignSaveRequest("site-1", "X", null, null, "completed");

        assertThatThrownBy(() -> service.update("site-1", "c-1", req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("INVALID_STATE_TRANSITION");
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void update_invalid_time_window_throws() {
        stubSiteOk("site-1");
        Campaign existing = new Campaign();
        existing.setId("c-1");
        existing.setSiteId("site-1");
        existing.setName("X");
        existing.setStatus("active");
        when(campaignRepository.findById("c-1", "site-1")).thenReturn(Optional.of(CampaignAggregate.reconstitute(existing, null)));
        CampaignSaveRequest req = new CampaignSaveRequest(
                "site-1", "X", t("2026-08-31T00:00:00Z"), t("2026-08-01T00:00:00Z"), null);

        assertThatThrownBy(() -> service.update("site-1", "c-1", req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("INVALID_TIME_WINDOW");
    }

    @Test
    void update_not_found_throws() {
        stubSiteOk("site-1");
        when(campaignRepository.findById("c-x", "site-1")).thenReturn(Optional.empty());
        CampaignSaveRequest req = new CampaignSaveRequest("site-1", "X", null, null, null);

        assertThatThrownBy(() -> service.update("site-1", "c-x", req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("CAMPAIGN_NOT_FOUND");
    }

    @Test
    void delete_succeeds_when_no_channels() {
        stubSiteOk("site-1");
        Campaign existing = new Campaign();
        existing.setId("c-1");
        existing.setSiteId("site-1");
        when(campaignRepository.findById("c-1", "site-1")).thenReturn(Optional.of(CampaignAggregate.reconstitute(existing, null)));
        when(campaignRepository.hasChannels("c-1")).thenReturn(false);

        service.delete("site-1", "c-1");

        verify(campaignRepository).hasChannels("c-1");
        verify(campaignRepository).deleteByIdAndSiteId("c-1", "site-1");
    }

    @Test
    void delete_throws_when_has_channels() {
        stubSiteOk("site-1");
        Campaign existing = new Campaign();
        existing.setId("c-1");
        existing.setSiteId("site-1");
        when(campaignRepository.findById("c-1", "site-1")).thenReturn(Optional.of(CampaignAggregate.reconstitute(existing, null)));
        when(campaignRepository.hasChannels("c-1")).thenReturn(true);

        assertThatThrownBy(() -> service.delete("site-1", "c-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("CAMPAIGN_HAS_CHANNELS");
        verify(campaignRepository, never()).deleteByIdAndSiteId(anyString(), anyString());
    }

    @Test
    void delete_not_found_throws() {
        stubSiteOk("site-1");
        when(campaignRepository.findById("c-x", "site-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("site-1", "c-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("CAMPAIGN_NOT_FOUND");
    }
}
