package com.luban.backend.operatorside.service;

import com.luban.backend.shared.entity.SystemSettingsRow;
import com.luban.backend.shared.mapper.SystemSettingsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SettingsService 单测（backend-ddd-refactor plan v2 T16，重写）。
 *
 * <p><b>重写原因</b>：旧测试用 @SpringBootTest + try/catch 吞异常，实质零有效断言（测试环境无 Redis，
 * update 必抛异常被 catch，等于空跑）。改为纯 Mockito 单测，覆盖所有分支。
 *
 * <p>覆盖 get：缓存命中 / 缓存 miss + DB 命中 / DB null → DEFAULT_JSON / DB row 但 dataJson null /
 * 缓存命中但值为 null（穿透到 DB）。
 * 覆盖 update：insert 新行 / update 已有行 / dataJson null → DEFAULT_JSON / 缓存刷新。
 *
 * <p>Mockito strict stubbing：每个测试只 stub 它实际会走的路径用到的 mock，避免 UnnecessaryStubbing。
 */
@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock private SystemSettingsMapper settingsMapper;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private SettingsService service;

    @BeforeEach
    void setUp() {
        service = new SettingsService(settingsMapper, redisTemplate);
    }

    @Test
    void get_returns_cached_value_on_cache_hit() {
        when(redisTemplate.hasKey("settings:global")).thenReturn(Boolean.TRUE);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("settings:global")).thenReturn("{\"siteName\":\"Cached\"}");

        String result = service.get();

        assertThat(result).isEqualTo("{\"siteName\":\"Cached\"}");
        verify(settingsMapper, never()).get();
    }

    @Test
    void get_falls_back_to_db_when_cache_miss() {
        when(redisTemplate.hasKey("settings:global")).thenReturn(Boolean.FALSE);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        SystemSettingsRow row = new SystemSettingsRow();
        row.setDataJson("{\"siteName\":\"DB\"}");
        when(settingsMapper.get()).thenReturn(row);

        String result = service.get();

        assertThat(result).isEqualTo("{\"siteName\":\"DB\"}");
        verify(valueOps).set("settings:global", "{\"siteName\":\"DB\"}");
    }

    @Test
    void get_returns_default_when_db_empty() {
        when(redisTemplate.hasKey("settings:global")).thenReturn(Boolean.FALSE);
        when(settingsMapper.get()).thenReturn(null);

        String result = service.get();

        assertThat(result).isEqualTo("{}");
        // DB 空 → return DEFAULT_JSON，不回填缓存（不触发 opsForValue）
    }

    @Test
    void get_returns_default_when_db_row_has_null_data() {
        when(redisTemplate.hasKey("settings:global")).thenReturn(Boolean.FALSE);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        SystemSettingsRow row = new SystemSettingsRow();
        row.setDataJson(null);
        when(settingsMapper.get()).thenReturn(row);

        String result = service.get();

        assertThat(result).isEqualTo("{}");
        verify(valueOps).set(eq("settings:global"), eq("{}"));
    }

    @Test
    void get_penetrates_to_db_when_cache_value_null() {
        when(redisTemplate.hasKey("settings:global")).thenReturn(Boolean.TRUE);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("settings:global")).thenReturn(null);
        SystemSettingsRow row = new SystemSettingsRow();
        row.setDataJson("{\"siteName\":\"DB\"}");
        when(settingsMapper.get()).thenReturn(row);

        String result = service.get();

        assertThat(result).isEqualTo("{\"siteName\":\"DB\"}");
        verify(valueOps).set("settings:global", "{\"siteName\":\"DB\"}");
    }

    @Test
    void update_inserts_new_row_when_db_empty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(settingsMapper.get()).thenReturn(null);

        String result = service.update("{\"siteName\":\"New\"}");

        assertThat(result).isEqualTo("{\"siteName\":\"New\"}");
        verify(settingsMapper).insert(any(SystemSettingsRow.class));
        verify(settingsMapper, never()).update(any());
        verify(valueOps).set("settings:global", "{\"siteName\":\"New\"}");
    }

    @Test
    void update_updates_existing_row() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        SystemSettingsRow existing = new SystemSettingsRow();
        existing.setId(1);
        existing.setDataJson("{\"old\":true}");
        when(settingsMapper.get()).thenReturn(existing);

        String result = service.update("{\"siteName\":\"Updated\"}");

        assertThat(result).isEqualTo("{\"siteName\":\"Updated\"}");
        verify(settingsMapper).update(any(SystemSettingsRow.class));
        verify(settingsMapper, never()).insert(any());
        verify(valueOps).set("settings:global", "{\"siteName\":\"Updated\"}");
    }

    @Test
    void update_uses_default_when_dataJson_null() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(settingsMapper.get()).thenReturn(null);

        String result = service.update(null);

        assertThat(result).isEqualTo("{}");
        verify(valueOps).set("settings:global", "{}");
    }
}
