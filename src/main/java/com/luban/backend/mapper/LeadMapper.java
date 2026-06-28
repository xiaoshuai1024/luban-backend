package com.luban.backend.mapper;

import com.luban.backend.entity.Lead;
import org.apache.ibatis.annotations.*;

import java.time.Instant;
import java.util.List;

/**
 * Lead 线索 Mapper（MyBatis 注解，含去重查询、分页列表、状态更新）。
 */
@Mapper
public interface LeadMapper {

    String COLS = "id, site_id, form_id, page_id, channel_id, contact_json, utm_json, status, "
            + "assignee_id, dedup_hash, source_ip, visitor_id, converted_at, created_at, updated_at";

    @Insert("INSERT INTO leads (id, site_id, form_id, page_id, channel_id, contact_json, utm_json, status, "
            + "assignee_id, dedup_hash, source_ip, visitor_id, converted_at, created_at, updated_at) "
            + "VALUES (#{id}, #{siteId}, #{formId}, #{pageId}, #{channelId}, #{contactJson}, #{utmJson}, #{status}, "
            + "#{assigneeId}, #{dedupHash}, #{sourceIp}, #{visitorId}, #{convertedAt}, #{createdAt}, #{updatedAt})")
    int insert(Lead lead);

    @Select("SELECT " + COLS + " FROM leads WHERE id = #{id} AND site_id = #{siteId}")
    Lead getByIdAndSiteId(@Param("id") String id, @Param("siteId") String siteId);

    /** 去重查询：窗口内同 form + dedup_hash 的线索数。 */
    @Select("SELECT COUNT(*) FROM leads WHERE form_id = #{formId} AND dedup_hash = #{hash} "
            + "AND created_at >= DATE_SUB(NOW(), INTERVAL #{windowSeconds} SECOND)")
    int countByFormHashInWindow(@Param("formId") String formId, @Param("hash") String hash,
                                @Param("windowSeconds") int windowSeconds);

    @Select("<script>"
            + "SELECT " + COLS + " FROM leads WHERE site_id = #{siteId}"
            + "<if test='status != null and status != \"\"'> AND status = #{status}</if>"
            + "<if test='formId != null and formId != \"\"'> AND form_id = #{formId}</if>"
            + "<if test='assigneeId != null and assigneeId != \"\"'> AND assignee_id = #{assigneeId}</if>"
            + " ORDER BY created_at DESC LIMIT #{offset}, #{limit}"
            + "</script>")
    List<Lead> listByQuery(@Param("siteId") String siteId,
                           @Param("status") String status,
                           @Param("formId") String formId,
                           @Param("assigneeId") String assigneeId,
                           @Param("offset") int offset,
                           @Param("limit") int limit);

    @Select("<script>"
            + "SELECT COUNT(*) FROM leads WHERE site_id = #{siteId}"
            + "<if test='status != null and status != \"\"'> AND status = #{status}</if>"
            + "<if test='formId != null and formId != \"\"'> AND form_id = #{formId}</if>"
            + "<if test='assigneeId != null and assigneeId != \"\"'> AND assignee_id = #{assigneeId}</if>"
            + "</script>")
    int countByQuery(@Param("siteId") String siteId,
                     @Param("status") String status,
                     @Param("formId") String formId,
                     @Param("assigneeId") String assigneeId);

    @Update("UPDATE leads SET status = #{status}, assignee_id = #{assigneeId}, "
            + "converted_at = #{convertedAt}, updated_at = #{updatedAt} "
            + "WHERE id = #{id} AND site_id = #{siteId}")
    int updateStatus(@Param("id") String id, @Param("siteId") String siteId,
                     @Param("status") String status, @Param("assigneeId") String assigneeId,
                     @Param("convertedAt") Instant convertedAt, @Param("updatedAt") Instant updatedAt);

    @Select("SELECT " + COLS + " FROM leads WHERE site_id = #{siteId} ORDER BY created_at DESC LIMIT 10000")
    List<Lead> listAllForExport(@Param("siteId") String siteId);

    /** 导出（带筛选，修复 🔴 export 忽略 filter 参数）。🟡 增 LIMIT 10000 防 OOM。 */
    @Select("<script>"
            + "SELECT " + COLS + " FROM leads WHERE site_id = #{siteId}"
            + "<if test='status != null and status != \"\"'> AND status = #{status}</if>"
            + "<if test='formId != null and formId != \"\"'> AND form_id = #{formId}</if>"
            + "<if test='assigneeId != null and assigneeId != \"\"'> AND assignee_id = #{assigneeId}</if>"
            + " ORDER BY created_at DESC LIMIT 10000"
            + "</script>")
    List<Lead> listForExport(@Param("siteId") String siteId,
                             @Param("status") String status,
                             @Param("formId") String formId,
                             @Param("assigneeId") String assigneeId);

    /**
     * 查询窗口内同 form+dedup_hash 的线索（用于 OVERWRITE/MERGE 去重策略，T-be-4）。
     * 返回命中记录（最多 1 条，因 uk_form_dedup 唯一约束）。
     */
    @Select("SELECT " + COLS + " FROM leads WHERE form_id = #{formId} AND dedup_hash = #{hash} "
            + "AND created_at >= DATE_SUB(NOW(), INTERVAL #{windowSeconds} SECOND) "
            + "ORDER BY created_at DESC LIMIT 1")
    Lead getByFormHashInWindow(@Param("formId") String formId, @Param("hash") String hash,
                               @Param("windowSeconds") int windowSeconds);

    /** 按 form+hash 删除窗内记录（OVERWRITE 策略：删旧插新，T-be-4）。
     *  仅删除窗口内的记录，保留历史线索（修复 🔴 delete 跨窗口误删）。 */
    @Delete("DELETE FROM leads WHERE form_id = #{formId} AND dedup_hash = #{hash} "
            + "AND created_at >= DATE_SUB(NOW(), INTERVAL #{windowSeconds} SECOND)")
    int deleteByFormHashInWindow(@Param("formId") String formId, @Param("hash") String hash,
                                 @Param("windowSeconds") int windowSeconds);

    /** 按 id 删除审计/线索（MERGE 策略更新旧记录后无须删除；备用）。 */
    @Update("UPDATE leads SET contact_json = #{contactJson}, utm_json = #{utmJson}, "
            + "updated_at = #{updatedAt} WHERE id = #{id} AND site_id = #{siteId}")
    int updateContact(@Param("id") String id, @Param("siteId") String siteId,
                      @Param("contactJson") String contactJson, @Param("utmJson") String utmJson,
                      @Param("updatedAt") java.time.Instant updatedAt);

    /** V2 级联删除：删站点时先清 leads */
    @Delete("DELETE FROM leads WHERE site_id = #{siteId}")
    int deleteBySiteId(@Param("siteId") String siteId);

    /** 按 formId 统计线索数（删表单前级联校验用） */
    @Select("SELECT COUNT(*) FROM leads WHERE form_id = #{formId}")
    int countByFormId(@Param("formId") String formId);
}
