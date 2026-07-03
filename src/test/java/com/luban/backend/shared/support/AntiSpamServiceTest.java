package com.luban.backend.shared.support;
import com.luban.backend.shared.support.RateLimitExecutor;
import com.luban.backend.shared.support.AntiSpamService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AntiSpamService 单测：mock {@link RateLimitExecutor}（普通接口，规避 JDK 23 下
 * mock {@code StringRedisTemplate} 的 Mockito inline 限制）。
 * 验证参数校验短路 + 滑动窗口计数→阈值判断语义。
 */
@ExtendWith(MockitoExtension.class)
class AntiSpamServiceTest {

    @Mock
    private RateLimitExecutor executor;

    @InjectMocks
    private AntiSpamService service;

    @Test
    void firstRequestNotLimited() {
        when(executor.countInWindow(anyString(), anyLong(), anyLong(), anyString(), anyInt())).thenReturn(1L);
        assertThat(service.isRateLimited("1.2.3.4", "f1", 5, 60)).isFalse();
    }

    @Test
    void withinThresholdNotLimited() {
        when(executor.countInWindow(anyString(), anyLong(), anyLong(), anyString(), anyInt())).thenReturn(5L);
        assertThat(service.isRateLimited("1.2.3.4", "f1", 5, 60)).isFalse();
    }

    @Test
    void overThresholdLimited() {
        when(executor.countInWindow(anyString(), anyLong(), anyLong(), anyString(), anyInt())).thenReturn(6L);
        assertThat(service.isRateLimited("1.2.3.4", "f1", 5, 60)).isTrue();
    }

    @Test
    void nullIpSkipsRedis() {
        assertThat(service.isRateLimited(null, "f1", 5, 60)).isFalse();
        verifyNoInteractions(executor);
    }

    @Test
    void blankIpSkipsRedis() {
        assertThat(service.isRateLimited("  ", "f1", 5, 60)).isFalse();
        verifyNoInteractions(executor);
    }

    @Test
    void nonPositiveMaxSkipsRedis() {
        assertThat(service.isRateLimited("1.2.3.4", "f1", 0, 60)).isFalse();
        verifyNoInteractions(executor);
    }

    @Test
    void nonPositiveWindowSkipsRedis() {
        // 滑动窗口：windowSeconds <= 0 为非法窗口，短路跳过 Redis
        assertThat(service.isRateLimited("1.2.3.4", "f1", 5, 0)).isFalse();
        verifyNoInteractions(executor);
    }

    @Test
    void usesCompositeKeyWithFormAndIp() {
        // 验证 key 由 formId + ip 组合（防维度串扰）
        when(executor.countInWindow(anyString(), anyLong(), anyLong(), anyString(), anyInt())).thenReturn(1L);
        service.isRateLimited("1.2.3.4", "f1", 5, 60);
        verify(executor).countInWindow(
                org.mockito.ArgumentMatchers.eq("antispam:form:f1:ip:1.2.3.4"),
                anyLong(), anyLong(), anyString(), anyInt());
    }
}
