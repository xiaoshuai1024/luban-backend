package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.dto.SiteConfigDto;
import com.luban.backend.shared.mapper.SiteMapper;
import com.luban.backend.shared.port.SiteConfigReadPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link SiteConfigReadPort} 实现：封装 {@link SiteMapper#getBySlug} 的站点配置读路径。
 * 返回轻量 DTO（不含完整 Site entity），供 publicside controller 使用。
 */
@Component
public class SiteConfigReadAdapter implements SiteConfigReadPort {

    private final SiteMapper siteMapper;

    public SiteConfigReadAdapter(SiteMapper siteMapper) {
        this.siteMapper = siteMapper;
    }

    @Override
    public Optional<SiteConfigDto> findBySlug(String slug) {
        var site = siteMapper.getBySlug(slug);
        if (site == null) return Optional.empty();
        return Optional.of(new SiteConfigDto(site.getName(), site.getBaseUrl(), site.getAnalyticsJson()));
    }
}
