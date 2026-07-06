package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.entity.PublishedPage;
import com.luban.backend.shared.mapper.PublishedPageMapper;
import com.luban.backend.shared.port.PublishedPageReadPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link PublishedPageReadPort} 实现：封装 {@link PublishedPageMapper} 的读路径。
 * 写侧 upsert 仍由 {@code PublishedPageProjection} 负责；本 Adapter 仅暴露 publicside 所需只读查询。
 */
@Component
public class PublishedPageReadAdapter implements PublishedPageReadPort {

    private final PublishedPageMapper publishedPageMapper;

    public PublishedPageReadAdapter(PublishedPageMapper publishedPageMapper) {
        this.publishedPageMapper = publishedPageMapper;
    }

    @Override
    public Optional<PublishedPage> findBySiteIdAndPath(String siteId, String path) {
        return Optional.ofNullable(publishedPageMapper.getBySiteIdAndPath(siteId, path));
    }
}
