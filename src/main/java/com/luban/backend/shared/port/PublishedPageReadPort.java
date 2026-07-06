package com.luban.backend.shared.port;

import com.luban.backend.shared.entity.PublishedPage;

import java.util.Optional;

/**
 * 发布页只读 Port（publicside 依赖倒置）。
 *
 * <p>publicside 公开页渲染需读 published_pages 投影，但不能直连 {@code PublishedPageMapper}
 * （operatorside 写侧职责）。Port 接口置于 shared，实现置于 operatorside，publicside 仅依赖接口。
 * 与 {@code PublishedPageProjection}（写侧 upsert）共享 Mapper 但读路径独立。
 */
public interface PublishedPageReadPort {

    Optional<PublishedPage> findBySiteIdAndPath(String siteId, String path);
}
