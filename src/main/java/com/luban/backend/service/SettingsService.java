package com.luban.backend.service;

import com.luban.backend.entity.SystemSettingsRow;
import com.luban.backend.exception.BusinessException;
import com.luban.backend.mapper.SystemSettingsMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * System settings with Redis cache at key settings:global (aligned with Go).
 */
@Service
public class SettingsService {

    private static final String CACHE_KEY = "settings:global";
    private static final String DEFAULT_JSON = "{}";

    private final SystemSettingsMapper settingsMapper;
    private final StringRedisTemplate redisTemplate;

    public SettingsService(SystemSettingsMapper settingsMapper, StringRedisTemplate redisTemplate) {
        this.settingsMapper = settingsMapper;
        this.redisTemplate = redisTemplate;
    }

    public String get() {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(CACHE_KEY))) {
            String cached = redisTemplate.opsForValue().get(CACHE_KEY);
            if (cached != null) return cached;
        }
        SystemSettingsRow row = settingsMapper.get();
        if (row == null) return DEFAULT_JSON;
        String data = row.getDataJson() != null ? row.getDataJson() : DEFAULT_JSON;
        redisTemplate.opsForValue().set(CACHE_KEY, data);
        return data;
    }

    public String update(String dataJson) {
        if (dataJson == null) dataJson = DEFAULT_JSON;
        Instant now = Instant.now();
        SystemSettingsRow row = settingsMapper.get();
        if (row == null) {
            row = new SystemSettingsRow();
            row.setId(1);
            row.setDataJson(dataJson);
            row.setUpdatedAt(now);
            settingsMapper.insert(row);
        } else {
            row.setDataJson(dataJson);
            row.setUpdatedAt(now);
            settingsMapper.update(row);
        }
        redisTemplate.opsForValue().set(CACHE_KEY, dataJson);
        return dataJson;
    }
}
