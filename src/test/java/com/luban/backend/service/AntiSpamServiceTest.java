package com.luban.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AntiSpamService 单测：mock StringRedisTemplate 的 execute（Lua 脚本路径）。
 * 🟡 修复后改用 Lua 原子 INCR+EXPIRE。
 */
@ExtendWith(MockitoExtension.class)
class AntiSpamServiceTest {

    @Mock
    private StringRedisTemplate redis;

    private AntiSpamService service() {
        return new AntiSpamService(redis);
    }

    @SuppressWarnings("unchecked")
    @Test
    void firstRequestNotLimited() {
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);
        assertThat(service().isRateLimited("1.2.3.4", "f1", 5, 60)).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    void withinThresholdNotLimited() {
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(5L);
        assertThat(service().isRateLimited("1.2.3.4", "f1", 5, 60)).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    void overThresholdLimited() {
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(6L);
        assertThat(service().isRateLimited("1.2.3.4", "f1", 5, 60)).isTrue();
    }

    @Test
    void nullIpSkipsRedis() {
        assertThat(service().isRateLimited(null, "f1", 5, 60)).isFalse();
        verifyNoInteractions(redis);
    }

    @Test
    void blankIpSkipsRedis() {
        assertThat(service().isRateLimited("  ", "f1", 5, 60)).isFalse();
        verifyNoInteractions(redis);
    }

    @Test
    void nonPositiveMaxSkipsRedis() {
        assertThat(service().isRateLimited("1.2.3.4", "f1", 0, 60)).isFalse();
        verifyNoInteractions(redis);
    }
}
