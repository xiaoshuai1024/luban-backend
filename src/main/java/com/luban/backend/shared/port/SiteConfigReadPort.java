package com.luban.backend.shared.port;

import com.luban.backend.shared.dto.SiteConfigDto;

import java.util.Optional;

/**
 * 站点配置只读 Port（publicside 依赖倒置）。
 *
 * <p>publicside 站点配置接口需按 slug 读站点 name/baseUrl/analyticsJson，
 * 但不能直连 SiteMapper/Site entity。Port 接口置于 shared，实现置于 operatorside。
 */
public interface SiteConfigReadPort {

    /** 按 slug 读站点配置（name/baseUrl/analyticsJson）；不存在返回 empty。 */
    Optional<SiteConfigDto> findBySlug(String slug);
}
