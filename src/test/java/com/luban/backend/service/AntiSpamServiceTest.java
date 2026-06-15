package com.luban.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AntiSpamService 单测：用 Mockito mock StringRedisTemplate，覆盖固定窗口频控分支。
 * 不依赖 Redis 实际运行。
 */
@ExtendWith(MockitoExtension.class)
class AntiSpamServiceTest {

    private static final String KEY = "antispam:form:f1:ip:1.2.3.4";

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private AntiSpamService service() {
        return new AntiSpamService(redis);
    }

    @Test
    void firstRequestNotLimitedAndSetsExpire() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(KEY)).thenReturn(1L);

        assertThat(service().isRateLimited("1.2.3.4", "f1", 5, 60)).isFalse();
        verify(redis).expire(eq(KEY), eq(Duration.ofSeconds(60)));
    }

    @Test
    void withinThresholdNotLimited() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(KEY)).thenReturn(5L); // 恰好等于上限

        assertThat(service().isRateLimited("1.2.3.4", "f1", 5, 60)).isFalse();
        verify(redis, never()).expire(anyString(), any());
    }

    @Test
    void overThresholdLimited() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(KEY)).thenReturn(6L);

        assertThat(service().isRateLimited("1.2.3.4", "f1", 5, 60)).isTrue();
        verify(redis, never()).expire(anyString(), any());
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
