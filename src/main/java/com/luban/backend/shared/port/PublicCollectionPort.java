package com.luban.backend.shared.port;

import com.luban.backend.shared.dto.CollectionItemResponse;

import java.util.List;

/**
 * 公开集合读取端口（app-deeplink-backend-arch plan T2）。
 *
 * <p>定义 C 端（publicside）需要的公开集合数据读取能力契约。
 * 由 operatorside 的 {@code CollectionService} 实现，publicside 的
 * {@code PublicController} 仅依赖此接口，不直接依赖 operatorside 类。
 *
 * <p>依赖倒置：消除 publicside → operatorside 的直接依赖（plan §6.1 隔离）。
 */
public interface PublicCollectionPort {
    /**
     * 查询站点下指定集合的公开 items（供 SSR 渲染）。
     *
     * @param slug         站点 slug
     * @param collectionId 集合 ID
     * @return 集合 items 列表
     */
    List<CollectionItemResponse> listPublicItems(String slug, String collectionId);
}
