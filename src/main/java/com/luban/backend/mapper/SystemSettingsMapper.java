package com.luban.backend.mapper;

import com.luban.backend.entity.SystemSettingsRow;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SystemSettingsMapper {

    @Select("SELECT id, data_json, updated_at FROM system_settings WHERE id = 1")
    SystemSettingsRow get();

    @Insert("INSERT INTO system_settings (id, data_json, updated_at) VALUES (1, #{dataJson}, #{updatedAt})")
    int insert(SystemSettingsRow row);

    @Update("UPDATE system_settings SET data_json = #{dataJson}, updated_at = #{updatedAt} WHERE id = 1")
    int update(SystemSettingsRow row);
}
