package com.luban.backend.mapper;

import com.luban.backend.entity.Collection;
import com.luban.backend.entity.CollectionItem;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * V2-T7 Collection + CollectionItem Mapper。
 * 列名显式列出（与 Go repo pageColumns 风格一致，避免新增列后列顺序敏感）。
 */
@Mapper
public interface CollectionMapper {

    // === Collection ===

    @Select("SELECT id, site_id, name, field_schema_json, status, created_at, updated_at FROM collections WHERE site_id = #{siteId} ORDER BY updated_at DESC")
    List<Collection> listBySiteId(String siteId);

    @Select("SELECT id, site_id, name, field_schema_json, status, created_at, updated_at FROM collections WHERE id = #{id} AND site_id = #{siteId}")
    Collection getByIdAndSiteId(@Param("id") String id, @Param("siteId") String siteId);

    @Insert("INSERT INTO collections (id, site_id, name, field_schema_json, status, created_at, updated_at) " +
            "VALUES (#{id}, #{siteId}, #{name}, #{fieldSchemaJson}, #{status}, #{createdAt}, #{updatedAt})")
    int insert(Collection collection);

    @Update("UPDATE collections SET name=#{name}, field_schema_json=#{fieldSchemaJson}, status=#{status}, updated_at=#{updatedAt} WHERE id=#{id} AND site_id=#{siteId}")
    int update(Collection collection);

    @Delete("DELETE FROM collections WHERE id = #{id} AND site_id = #{siteId}")
    int deleteByIdAndSiteId(@Param("id") String id, @Param("siteId") String siteId);

    // === CollectionItem ===

    @Select("SELECT id, collection_id, data_json, status, created_at, updated_at FROM collection_items WHERE collection_id = #{collectionId} ORDER BY updated_at DESC")
    List<CollectionItem> listItemsByCollectionId(String collectionId);

    @Select("SELECT id, collection_id, data_json, status, created_at, updated_at FROM collection_items WHERE id = #{itemId} AND collection_id = #{collectionId}")
    CollectionItem getItemByIdAndCollectionId(@Param("itemId") String itemId, @Param("collectionId") String collectionId);

    @Insert("INSERT INTO collection_items (id, collection_id, data_json, status, created_at, updated_at) " +
            "VALUES (#{id}, #{collectionId}, #{dataJson}, #{status}, #{createdAt}, #{updatedAt})")
    int insertItem(CollectionItem item);

    @Update("UPDATE collection_items SET data_json=#{dataJson}, status=#{status}, updated_at=#{updatedAt} WHERE id=#{id} AND collection_id=#{collectionId}")
    int updateItem(CollectionItem item);

    @Delete("DELETE FROM collection_items WHERE id = #{itemId} AND collection_id = #{collectionId}")
    int deleteItemByIdAndCollectionId(@Param("itemId") String itemId, @Param("collectionId") String collectionId);
}
