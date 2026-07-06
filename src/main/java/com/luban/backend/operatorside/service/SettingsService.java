package com.luban.backend.operatorside.service;

import com.luban.backend.shared.entity.SystemSettingsRow;
import com.luban.backend.shared.repository.SystemSettingsRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * System settings with Redis cache at key settings:global (aligned with Go).
 * DB 访问经 {@link SystemSettingsRepository}，不直连 SystemSettingsMapper。
 */
@Service
public class SettingsService {

    private static final String CACHE_KEY = "settings:global";
    private static final String DEFAULT_JSON = "{}";

    private final SystemSettingsRepository settingsRepository;
    private final StringRedisTemplate redisTemplate;

    public SettingsService(SystemSettingsRepository settingsRepository, StringRedisTemplate redisTemplate) {
        this.settingsRepository = settingsRepository;
        this.redisTemplate = redisTemplate;
    }

    public String get() {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(CACHE_KEY))) {
            String cached = redisTemplate.opsForValue().get(CACHE_KEY);
            if (cached != null) return cached;
        }
        SystemSettingsRow row = settingsRepository.find().orElse(null);
        if (row == null) return DEFAULT_JSON;
        String data = row.getDataJson() != null ? row.getDataJson() : DEFAULT_JSON;
        redisTemplate.opsForValue().set(CACHE_KEY, data);
        return data;
    }

    public String update(String dataJson) {
        if (dataJson == null) dataJson = DEFAULT_JSON;
        Instant now = Instant.now();
        SystemSettingsRow row = settingsRepository.find().orElse(null);
        if (row == null) {
            row = new SystemSettingsRow();
            row.setId(1);
            row.setDataJson(dataJson);
            row.setUpdatedAt(now);
            settingsRepository.insert(row);
        } else {
            row.setDataJson(dataJson);
            row.setUpdatedAt(now);
            settingsRepository.update(row);
        }
        redisTemplate.opsForValue().set(CACHE_KEY, dataJson);
        return dataJson;
    }
}
