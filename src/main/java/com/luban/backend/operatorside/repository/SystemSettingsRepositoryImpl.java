package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.entity.SystemSettingsRow;
import com.luban.backend.shared.mapper.SystemSettingsMapper;
import com.luban.backend.shared.repository.SystemSettingsRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * SystemSettings 仓储实现：封装 {@link SystemSettingsMapper}。
 * 单行全局配置（id=1）；insert vs update 由 Service 层按 find() 判定。
 */
@Repository
public class SystemSettingsRepositoryImpl implements SystemSettingsRepository {

    private final SystemSettingsMapper settingsMapper;

    public SystemSettingsRepositoryImpl(SystemSettingsMapper settingsMapper) {
        this.settingsMapper = settingsMapper;
    }

    @Override
    public Optional<SystemSettingsRow> find() {
        return Optional.ofNullable(settingsMapper.get());
    }

    @Override
    public void insert(SystemSettingsRow row) {
        settingsMapper.insert(row);
    }

    @Override
    public void update(SystemSettingsRow row) {
        settingsMapper.update(row);
    }
}
