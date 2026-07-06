package com.luban.backend.publicside.service;

import com.luban.backend.shared.dto.ShortLinkResolveResult;
import com.luban.backend.shared.entity.Channel;
import com.luban.backend.shared.entity.Page;
import com.luban.backend.shared.entity.Site;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.port.ChannelReadPort;
import com.luban.backend.shared.repository.PageRepository;
import com.luban.backend.shared.repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * ChannelReadService 单测（backend-ddd-refactor plan v2 T16，补覆盖率）。
 *
 * <p>覆盖 resolve 的 7 个分支：
 * null/格式非法 → SHORT_LINK_NOT_FOUND / channel 不存在 → 404 / channel inactive → 410 /
 * page 不存在 → 404 / page archived → 503 TARGET_PAGE_UNAVAILABLE / site 不存在 → 404 /
 * 正常路径（含 utm 解析 + null utm）。
 *
 * <p>对齐 app-deeplink-backend-arch plan T14 状态语义（404 vs 410 vs 503 区分）。
 */
@ExtendWith(MockitoExtension.class)
class ChannelReadServiceTest {

    @Mock private ChannelReadPort channelReadPort;
    @Mock private PageRepository pageRepository;
    @Mock private SiteRepository siteRepository;

    private ChannelReadService service;

    @BeforeEach
    void setUp() {
        service = new ChannelReadService(channelReadPort, pageRepository, siteRepository);
    }

    private Channel activeChannel() {
        Channel c = new Channel();
        c.setId("ch-1");
        c.setSiteId("site-1");
        c.setCode("summer2024");
        c.setShortUrl("summer2024");
        c.setTargetPageId("page-1");
        c.setStatus("active");
        c.setUtmTemplate("{\"utm_source\":\"ad\"}");
        return c;
    }

    private Page publishedPage() {
        Page p = new Page();
        p.setId("page-1");
        p.setSiteId("site-1");
        p.setPath("/landing");
        p.setStatus("published");
        return p;
    }

    private Site activeSite() {
        Site s = new Site();
        s.setId("site-1");
        s.setSlug("acme");
        return s;
    }

    @Test
    void resolve_throws_when_shortUrl_null() {
        assertThatThrownBy(() -> service.resolve(null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("SHORT_LINK_NOT_FOUND");
    }

    @Test
    void resolve_throws_when_shortUrl_format_invalid() {
        // CODE_PATTERN = ^[a-zA-Z0-9_-]{1,32}$；含特殊字符 / 超长均非法
        assertThatThrownBy(() -> service.resolve("含中文"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("SHORT_LINK_NOT_FOUND");
        assertThatThrownBy(() -> service.resolve("a!b@c"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("SHORT_LINK_NOT_FOUND");
    }

    @Test
    void resolve_throws_404_when_channel_not_found() {
        when(channelReadPort.getByShortUrl("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("ghost"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("SHORT_LINK_NOT_FOUND");
    }

    @Test
    void resolve_throws_410_when_channel_inactive() {
        Channel c = activeChannel();
        c.setStatus("inactive");
        when(channelReadPort.getByShortUrl("summer2024")).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.resolve("summer2024"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("SHORT_LINK_INACTIVE");
    }

    @Test
    void resolve_throws_404_when_target_page_not_found() {
        when(channelReadPort.getByShortUrl("summer2024")).thenReturn(Optional.of(activeChannel()));
        when(pageRepository.findEntityByIdAndSiteId("page-1", "site-1")).thenReturn(null);

        assertThatThrownBy(() -> service.resolve("summer2024"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("SHORT_LINK_NOT_FOUND");
    }

    @Test
    void resolve_throws_503_when_target_page_archived() {
        when(channelReadPort.getByShortUrl("summer2024")).thenReturn(Optional.of(activeChannel()));
        Page p = publishedPage();
        p.setStatus("archived");
        when(pageRepository.findEntityByIdAndSiteId("page-1", "site-1")).thenReturn(p);

        assertThatThrownBy(() -> service.resolve("summer2024"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("TARGET_PAGE_UNAVAILABLE");
    }

    @Test
    void resolve_throws_404_when_site_not_found() {
        when(channelReadPort.getByShortUrl("summer2024")).thenReturn(Optional.of(activeChannel()));
        when(pageRepository.findEntityByIdAndSiteId("page-1", "site-1")).thenReturn(publishedPage());
        when(siteRepository.findById("site-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("summer2024"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("SHORT_LINK_NOT_FOUND");
    }

    @Test
    void resolve_returns_result_with_parsed_utm_on_happy_path() {
        when(channelReadPort.getByShortUrl("summer2024")).thenReturn(Optional.of(activeChannel()));
        when(pageRepository.findEntityByIdAndSiteId("page-1", "site-1")).thenReturn(publishedPage());
        when(siteRepository.findById("site-1")).thenReturn(Optional.of(com.luban.backend.shared.domain.SiteAggregate.reconstitute(activeSite())));

        ShortLinkResolveResult result = service.resolve("summer2024");

        assertThat(result.siteSlug()).isEqualTo("acme");
        assertThat(result.pagePath()).isEqualTo("/landing");
        assertThat(result.channelCode()).isEqualTo("summer2024");
        assertThat(result.channelId()).isEqualTo("ch-1");
        assertThat(result.utmTemplate().get("utm_source").asText()).isEqualTo("ad");
    }

    @Test
    void resolve_returns_null_utm_when_template_blank() {
        Channel c = activeChannel();
        c.setUtmTemplate("   ");
        when(channelReadPort.getByShortUrl("summer2024")).thenReturn(Optional.of(c));
        when(pageRepository.findEntityByIdAndSiteId("page-1", "site-1")).thenReturn(publishedPage());
        when(siteRepository.findById("site-1")).thenReturn(Optional.of(com.luban.backend.shared.domain.SiteAggregate.reconstitute(activeSite())));

        ShortLinkResolveResult result = service.resolve("summer2024");

        assertThat(result.utmTemplate()).isNull();
    }

    @Test
    void resolve_returns_null_utm_when_template_invalid_json() {
        Channel c = activeChannel();
        c.setUtmTemplate("not-json{");
        when(channelReadPort.getByShortUrl("summer2024")).thenReturn(Optional.of(c));
        when(pageRepository.findEntityByIdAndSiteId("page-1", "site-1")).thenReturn(publishedPage());
        when(siteRepository.findById("site-1")).thenReturn(Optional.of(com.luban.backend.shared.domain.SiteAggregate.reconstitute(activeSite())));

        ShortLinkResolveResult result = service.resolve("summer2024");

        assertThat(result.utmTemplate()).isNull();   // parseJson 异常返回 null
    }
}
