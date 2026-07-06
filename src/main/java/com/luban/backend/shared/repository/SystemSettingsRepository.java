package com.luban.backend.shared.repository;

import com.luban.backend.shared.entity.SystemSettingsRow;

import java.util.Optional;

/**
 * SystemSettings 仓储接口（domain 抽象，不依赖 MyBatis）。
 *
 * <p>system_settings 表为单行（id=1）全局配置，由 Service 层判定 insert vs update。
 */
public interface SystemSettingsRepository {

    Optional<SystemSettingsRow> find();

    void insert(SystemSettingsRow row);

    void update(SystemSettingsRow row);
}
