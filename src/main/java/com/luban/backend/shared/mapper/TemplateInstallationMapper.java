package com.luban.backend.shared.mapper;

import com.luban.backend.shared.entity.TemplateInstallation;
import org.apache.ibatis.annotations.*;

/**
 * TemplateInstallation mapper（template-marketplace plan）。
 * 安装记录（审计 + 计数）。
 */
@Mapper
public interface TemplateInstallationMapper {

    String COLUMNS = "id, template_id, version, site_id, page_id, installer_id, created_at";

    @Insert("INSERT INTO template_installations (id, template_id, version, site_id, page_id, installer_id, created_at) " +
            "VALUES (#{id}, #{templateId}, #{version}, #{siteId}, #{pageId}, #{installerId}, #{createdAt})")
    int insert(TemplateInstallation inst);

    /** 模板安装次数（市场展示用） */
    @Select("SELECT COUNT(*) FROM template_installations WHERE template_id = #{templateId}")
    int countByTemplateId(String templateId);
}
