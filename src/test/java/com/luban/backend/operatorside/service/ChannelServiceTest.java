package com.luban.backend.operatorside.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.luban.backend.shared.domain.CampaignAggregate;
import com.luban.backend.shared.dto.ChannelResponse;
import com.luban.backend.shared.dto.ChannelSaveRequest;
import com.luban.backend.shared.entity.Channel;
import com.luban.backend.shared.entity.Page;
import com.luban.backend.shared.entity.Site;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.ChannelMapper;
import com.luban.backend.shared.mapper.PageMapper;
import com.luban.backend.shared.mapper.SiteMapper;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelServiceTest {

    @Mock private ChannelMapper channelMapper;
    @Mock private SiteMapper siteMapper;
    @Mock private PageMapper pageMapper;
    @Mock private TenantGuardService tenantGuard;

    private ChannelService service;
    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new ChannelService(channelMapper, siteMapper, pageMapper, tenantGuard);
    }

    private void stubSiteOk(String siteId) {
        when(siteMapper.getById(siteId)).thenReturn(new Site());
        doNothing().when(tenantGuard).ensureSiteAccess(siteId);
    }

    private Page pageOf(String siteId) {
        Page p = new Page();
        p.setId("page-1");
        p.setSiteId(siteId);
        return p;
    }

    private ObjectNode utmNode() {
        return JSON.createObjectNode().put("utm_source", "wechat");
    }

    @Test
    void list_returns_all_channels_for_site() {
        stubSiteOk("site-1");
        Channel ch = new Channel();
        ch.setId("ch-1");
        ch.setSiteId("site-1");
        ch.setCode("abc");
        ch.setType(CampaignAggregate.ChannelType.H5);
        ch.setUtmTemplate("{\"utm_source\":\"wechat\"}");
        ch.setShortUrl("abc");
        ch.setStatus("active");
        when(channelMapper.listBySiteId("site-1")).thenReturn(List.of(ch));

        List<ChannelResponse> out = service.list("site-1");

        assertThat(out).hasSize(1);
        assertThat(out.get(0).id()).isEqualTo("ch-1");
        assertThat(out.get(0).code()).isEqualTo("abc");
    }

    @Test
    void list_throws_site_not_found() {
        when(siteMapper.getById("site-x")).thenReturn(null);

        assertThatThrownBy(() -> service.list("site-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("SITE_NOT_FOUND");
        verifyNoInteractions(channelMapper);
    }

    @Test
    void get_returns_channel() {
        stubSiteOk("site-1");
        Channel ch = new Channel();
        ch.setId("ch-1");
        ch.setSiteId("site-1");
        ch.setCode("abc");
        ch.setType("h5");
        ch.setStatus("active");
        when(channelMapper.getByIdAndSiteId("ch-1", "site-1")).thenReturn(ch);

        ChannelResponse resp = service.get("site-1", "ch-1");

        assertThat(resp.id()).isEqualTo("ch-1");
        assertThat(resp.code()).isEqualTo("abc");
    }

    @Test
    void get_throws_channel_not_found() {
        stubSiteOk("site-1");
        when(channelMapper.getByIdAndSiteId("ch-x", "site-1")).thenReturn(null);

        assertThatThrownBy(() -> service.get("site-1", "ch-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("CHANNEL_NOT_FOUND");
    }

    @Test
    void create_with_operator_code_inserts_and_returns() {
        stubSiteOk("site-1");
        when(pageMapper.getByIdAndSiteId("page-1", "site-1")).thenReturn(pageOf("site-1"));
        ChannelSaveRequest req = new ChannelSaveRequest(
                "site-1", "mycode", "page-1", CampaignAggregate.ChannelType.QRCODE,
                utmNode(), "camp-1", null);

        ChannelResponse resp = service.create(req);

        assertThat(resp.code()).isEqualTo("mycode");
        assertThat(resp.shortUrl()).isEqualTo("mycode");
        assertThat(resp.status()).isEqualTo("active");
        verify(channelMapper).insert(any(Channel.class));
    }

    @Test
    void create_with_generated_code_succeeds_first_try() {
        stubSiteOk("site-1");
        when(pageMapper.getByIdAndSiteId("page-1", "site-1")).thenReturn(pageOf("site-1"));
        ChannelSaveRequest req = new ChannelSaveRequest(
                "site-1", null, "page-1", CampaignAggregate.ChannelType.H5, utmNode(), null, null);

        ChannelResponse resp = service.create(req);

        assertThat(resp.code()).hasSize(6);
        assertThat(resp.code()).matches("[a-zA-Z0-9]{6}");
        assertThat(resp.shortUrl()).isEqualTo(resp.code());
        verify(channelMapper).insert(any(Channel.class));
    }

    @Test
    void create_generated_code_retries_then_succeeds() {
        stubSiteOk("site-1");
        when(pageMapper.getByIdAndSiteId("page-1", "site-1")).thenReturn(pageOf("site-1"));
        // 前 2 次碰撞，第 3 次成功（when().thenThrow().thenReturn() 链）
        when(channelMapper.insert(any(Channel.class)))
                .thenThrow(new DataIntegrityViolationException("uk_site_code"))
                .thenThrow(new DataIntegrityViolationException("uk_site_code"))
                .thenReturn(1);
        ChannelSaveRequest req = new ChannelSaveRequest(
                "site-1", null, "page-1", "h5", utmNode(), null, null);

        ChannelResponse resp = service.create(req);

        assertThat(resp.code()).hasSize(6);
        org.mockito.Mockito.verify(channelMapper, org.mockito.Mockito.times(3)).insert(any(Channel.class));
    }

    @Test
    void create_generated_code_retries_exhausted_throws_code_gen_failed() {
        stubSiteOk("site-1");
        when(pageMapper.getByIdAndSiteId("page-1", "site-1")).thenReturn(pageOf("site-1"));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("uk_site_code"))
                .when(channelMapper).insert(any(Channel.class));
        ChannelSaveRequest req = new ChannelSaveRequest(
                "site-1", null, "page-1", "h5", utmNode(), null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("CODE_GEN_FAILED");
        org.mockito.Mockito.verify(channelMapper, org.mockito.Mockito.times(3)).insert(any(Channel.class));
    }

    @Test
    void create_operator_code_duplicate_throws_channel_code_duplicate() {
        stubSiteOk("site-1");
        when(pageMapper.getByIdAndSiteId("page-1", "site-1")).thenReturn(pageOf("site-1"));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("uk_site_code"))
                .when(channelMapper).insert(any(Channel.class));
        ChannelSaveRequest req = new ChannelSaveRequest(
                "site-1", "mycode", "page-1", "h5", utmNode(), null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("CHANNEL_CODE_DUPLICATE");
    }

    @Test
    void create_invalid_type_throws_invalid_argument() {
        stubSiteOk("site-1");
        when(pageMapper.getByIdAndSiteId("page-1", "site-1")).thenReturn(pageOf("site-1"));
        ChannelSaveRequest req = new ChannelSaveRequest(
                "site-1", null, "page-1", "unknown_type", utmNode(), null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("INVALID_ARGUMENT");
        verify(channelMapper, never()).insert(any(Channel.class));
    }

    @Test
    void create_page_not_found_throws_page_not_found() {
        stubSiteOk("site-1");
        when(pageMapper.getByIdAndSiteId("page-x", "site-1")).thenReturn(null);
        ChannelSaveRequest req = new ChannelSaveRequest(
                "site-1", null, "page-x", "h5", utmNode(), null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("PAGE_NOT_FOUND");
        verify(channelMapper, never()).insert(any(Channel.class));
    }

    @Test
    void create_page_not_belong_to_site_throws() {
        stubSiteOk("site-1");
        Page p = new Page();
        p.setId("page-1");
        p.setSiteId("site-OTHER");
        when(pageMapper.getByIdAndSiteId("page-1", "site-1")).thenReturn(p);
        ChannelSaveRequest req = new ChannelSaveRequest(
                "site-1", null, "page-1", "h5", utmNode(), null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("PAGE_NOT_BELONG_TO_SITE");
    }

    @Test
    void create_invalid_code_format_throws() {
        stubSiteOk("site-1");
        when(pageMapper.getByIdAndSiteId("page-1", "site-1")).thenReturn(pageOf("site-1"));
        ChannelSaveRequest req = new ChannelSaveRequest(
                "site-1", "bad code!", "page-1", "h5", utmNode(), null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("INVALID_CODE_FORMAT");
    }

    @Test
    void update_changes_fields_and_status() {
        stubSiteOk("site-1");
        Channel existing = new Channel();
        existing.setId("ch-1");
        existing.setSiteId("site-1");
        existing.setType("h5");
        existing.setTargetPageId("page-1");
        existing.setStatus("active");
        when(channelMapper.getByIdAndSiteId("ch-1", "site-1")).thenReturn(existing);
        ChannelSaveRequest req = new ChannelSaveRequest(
                "site-1", null, null, "social", null, "camp-2", "inactive");

        ChannelResponse resp = service.update("site-1", "ch-1", req);

        assertThat(resp.type()).isEqualTo("social");
        assertThat(resp.status()).isEqualTo("inactive");
        assertThat(resp.campaignId()).isEqualTo("camp-2");
        verify(channelMapper).update(any(Channel.class));
    }

    @Test
    void update_changes_target_page_with_belonging_check() {
        stubSiteOk("site-1");
        Channel existing = new Channel();
        existing.setId("ch-1");
        existing.setSiteId("site-1");
        existing.setTargetPageId("page-old");
        existing.setType("h5");
        existing.setStatus("active");
        when(channelMapper.getByIdAndSiteId("ch-1", "site-1")).thenReturn(existing);
        when(pageMapper.getByIdAndSiteId("page-new", "site-1")).thenReturn(pageOf("site-1"));
        ChannelSaveRequest req = new ChannelSaveRequest(
                "site-1", null, "page-new", null, null, null, null);

        ChannelResponse resp = service.update("site-1", "ch-1", req);

        assertThat(resp.targetPageId()).isEqualTo("page-new");
    }

    @Test
    void update_invalid_type_throws() {
        stubSiteOk("site-1");
        Channel existing = new Channel();
        existing.setId("ch-1");
        existing.setSiteId("site-1");
        existing.setType("h5");
        existing.setStatus("active");
        when(channelMapper.getByIdAndSiteId("ch-1", "site-1")).thenReturn(existing);
        ChannelSaveRequest req = new ChannelSaveRequest(
                "site-1", null, null, "bogus", null, null, null);

        assertThatThrownBy(() -> service.update("site-1", "ch-1", req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("INVALID_ARGUMENT");
        verify(channelMapper, never()).update(any(Channel.class));
    }

    @Test
    void update_channel_not_found_throws() {
        stubSiteOk("site-1");
        when(channelMapper.getByIdAndSiteId("ch-x", "site-1")).thenReturn(null);
        ChannelSaveRequest req = new ChannelSaveRequest("site-1", null, null, "h5", null, null, null);

        assertThatThrownBy(() -> service.update("site-1", "ch-x", req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("CHANNEL_NOT_FOUND");
    }

    @Test
    void delete_succeeds() {
        stubSiteOk("site-1");
        Channel existing = new Channel();
        existing.setId("ch-1");
        existing.setSiteId("site-1");
        when(channelMapper.getByIdAndSiteId("ch-1", "site-1")).thenReturn(existing);

        service.delete("site-1", "ch-1");

        verify(channelMapper).deleteByIdAndSiteId("ch-1", "site-1");
    }

    @Test
    void delete_not_found_throws() {
        stubSiteOk("site-1");
        when(channelMapper.getByIdAndSiteId("ch-x", "site-1")).thenReturn(null);

        assertThatThrownBy(() -> service.delete("site-1", "ch-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("CHANNEL_NOT_FOUND");
        verify(channelMapper, never()).deleteByIdAndSiteId(anyString(), anyString());
    }
}
