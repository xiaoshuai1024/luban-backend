package com.luban.backend.shared.mapper;

import com.luban.backend.shared.entity.LeadAuditLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * LeadAuditLog Mapper（MyBatis 注解）：审计日志写入 + 查询（按线索）。
 */
@Mapper
public interface LeadAuditLogMapper {

    String COLS = "id, site_id, lead_id, actor_id, action, detail, created_at";

    @Insert("INSERT INTO lead_audit_logs (id, site_id, lead_id, actor_id, action, detail, created_at) "
            + "VALUES (#{id}, #{siteId}, #{leadId}, #{actorId}, #{action}, #{detail}, #{createdAt})")
    int insert(LeadAuditLog log);

    /** 按线索查询审计记录（按时间倒序）。 */
    @Select("SELECT " + COLS + " FROM lead_audit_logs WHERE site_id = #{siteId} AND lead_id = #{leadId} ORDER BY created_at DESC")
    List<LeadAuditLog> listByLead(@Param("siteId") String siteId, @Param("leadId") String leadId);
}
