package com.luban.backend.mapper;

import com.luban.backend.entity.ContentCollection;
import com.luban.backend.entity.ContentCollectionItem;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * V2-T7 ContentCollection + ContentCollectionItem Mapper。
 * 列名显式列出（与 Go repo pageColumns 风格一致，避免新增列后列顺序敏感）。
 */
@Mapper
public interface CollectionMapper {

    // === ContentCollection ===

    @Select("SELECT id, site_id, name, field_schema_json, status, created_at, updated_at FROM collections WHERE site_id = #{siteId} ORDER BY updated_at DESC")
    List<ContentCollection> listBySiteId(String siteId);

    @Select("SELECT id, site_id, name, field_schema_json, status, created_at, updated_at FROM collections WHERE id = #{id} AND site_id = #{siteId}")
    ContentCollection getByIdAndSiteId(@Param("id") String id, @Param("siteId") String siteId);

    @Insert("INSERT INTO collections (id, site_id, name, field_schema_json, status, created_at, updated_at) " +
            "VALUES (#{id}, #{siteId}, #{name}, #{fieldSchemaJson}, #{status}, #{createdAt}, #{updatedAt})")
    int insert(ContentCollection collection);

    @Update("UPDATE collections SET name=#{name}, field_schema_json=#{fieldSchemaJson}, status=#{status}, updated_at=#{updatedAt} WHERE id=#{id} AND site_id=#{siteId}")
    int update(ContentCollection collection);

    @Delete("DELETE FROM collections WHERE id = #{id} AND site_id = #{siteId}")
    int deleteByIdAndSiteId(@Param("id") String id, @Param("siteId") String siteId);

    /** V2 级联删除：删站点时先清 collections（items 由 FK CASCADE 自动清） */
    @Delete("DELETE FROM collections WHERE site_id = #{siteId}")
    int deleteBySiteId(@Param("siteId") String siteId);

    // === ContentCollectionItem ===

    @Select("SELECT id, collection_id, data_json, status, created_at, updated_at FROM collection_items WHERE collection_id = #{collectionId} ORDER BY updated_at DESC")
    List<ContentCollectionItem> listItemsByCollectionId(String collectionId);

    @Select("SELECT id, collection_id, data_json, status, created_at, updated_at FROM collection_items WHERE id = #{itemId} AND collection_id = #{collectionId}")
    ContentCollectionItem getItemByIdAndCollectionId(@Param("itemId") String itemId, @Param("collectionId") String collectionId);

    @Insert("INSERT INTO collection_items (id, collection_id, data_json, status, created_at, updated_at) " +
            "VALUES (#{id}, #{collectionId}, #{dataJson}, #{status}, #{createdAt}, #{updatedAt})")
    int insertItem(ContentCollectionItem item);

    @Update("UPDATE collection_items SET data_json=#{dataJson}, status=#{status}, updated_at=#{updatedAt} WHERE id=#{id} AND collection_id=#{collectionId}")
    int updateItem(ContentCollectionItem item);

    @Delete("DELETE FROM collection_items WHERE id = #{itemId} AND collection_id = #{collectionId}")
    int deleteItemByIdAndCollectionId(@Param("itemId") String itemId, @Param("collectionId") String collectionId);
}
