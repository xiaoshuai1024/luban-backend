package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.domain.CollectionAggregate;
import com.luban.backend.shared.entity.ContentCollection;
import com.luban.backend.shared.entity.ContentCollectionItem;
import com.luban.backend.shared.mapper.CollectionMapper;
import com.luban.backend.shared.repository.CollectionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 内容集合仓储实现（backend-ddd-refactor plan v2 T12）。
 *
 * <p>封装 {@link CollectionMapper}。save 时根据聚合根 pendingChanges 执行 item 的
 * insert/update/delete，然后 clearPendingItemChanges。
 */
@Repository
public class CollectionRepositoryImpl implements CollectionRepository {

    private final CollectionMapper collectionMapper;

    public CollectionRepositoryImpl(CollectionMapper collectionMapper) {
        this.collectionMapper = collectionMapper;
    }

    @Override
    public CollectionAggregate findByIdWithItems(String id, String siteId) {
        ContentCollection c = collectionMapper.getByIdAndSiteId(id, siteId);
        if (c == null) return null;
        List<ContentCollectionItem> items = collectionMapper.listItemsByCollectionId(id);
        return CollectionAggregate.reconstitute(c, items);
    }

    @Override
    public ContentCollection findById(String id, String siteId) {
        return collectionMapper.getByIdAndSiteId(id, siteId);
    }

    @Override
    public List<ContentCollection> listBySiteId(String siteId) {
        return collectionMapper.listBySiteId(siteId);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void save(CollectionAggregate aggregate) {
        ContentCollection root = aggregate.toCollection();
        if (collectionMapper.getByIdAndSiteId(root.getId(), root.getSiteId()) == null) {
            collectionMapper.insert(root);
        } else {
            int n = collectionMapper.update(root);
            if (n == 0) {
                throw new IllegalStateException("collection update affected 0 rows: id=" + root.getId());
            }
        }
        // 执行聚合根 pending item 变更
        for (CollectionAggregate.NewItemCommand cmd : aggregate.pendingInserts()) {
            collectionMapper.insertItem(cmd.item());
        }
        for (ContentCollectionItem updated : aggregate.pendingUpdates()) {
            int n = collectionMapper.updateItem(updated);
            if (n == 0) {
                throw new IllegalStateException("collection item update affected 0 rows: itemId=" + updated.getId());
            }
        }
        for (String deletedId : aggregate.pendingItemDeletes()) {
            collectionMapper.deleteItemByIdAndCollectionId(deletedId, root.getId());
        }
        aggregate.clearPendingItemChanges();
    }

    @Override
    public int delete(String id, String siteId) {
        return collectionMapper.deleteByIdAndSiteId(id, siteId);
    }

    @Override
    public List<ContentCollectionItem> listItems(String collectionId) {
        return collectionMapper.listItemsByCollectionId(collectionId);
    }

    @Override
    public ContentCollectionItem findItem(String itemId, String collectionId) {
        return collectionMapper.getItemByIdAndCollectionId(itemId, collectionId);
    }
}
