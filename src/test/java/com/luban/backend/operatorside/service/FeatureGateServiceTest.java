package com.luban.backend.operatorside.service;
import com.luban.backend.operatorside.service.FeatureGateService;

import com.luban.backend.shared.entity.FeatureGate;
import com.luban.backend.shared.mapper.FeatureGateMapper;
import com.luban.backend.shared.mapper.SubscriptionMapper;
import com.luban.backend.shared.mapper.UserSiteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FeatureGateService 单测（v01 T-be-7 + v02 T-be-3 plan 放行改造）。
 * v02 改造后构造器新增 UserSiteMapper/SubscriptionMapper/PlanService 依赖。
 * 测试在 findOwnerUserId 返回 null（历史站点无 owner）时验证向后兼容默认开启。
 */
@ExtendWith(MockitoExtension.class)
class FeatureGateServiceTest {

    @Mock private FeatureGateMapper featureGateMapper;
    @Mock private UserSiteMapper userSiteMapper;
    @Mock private SubscriptionMapper subscriptionMapper;
    @Mock private PlanService planService;

    private FeatureGateService service;

    @BeforeEach
    void setup() {
        service = new FeatureGateService(featureGateMapper, userSiteMapper, subscriptionMapper, planService);
        // 历史站点无 owner 记录 → 向后兼容默认开启（plan 判定被跳过）
        lenient().when(userSiteMapper.findOwnerUserId(anyString())).thenReturn(null);
    }

    @Test
    void isEnabledDefaultsToTrueWhenNotConfigured() {
        when(featureGateMapper.getBySiteAndKey("site-1", "lead_capture")).thenReturn(null);
        assertThat(service.isEnabled("site-1", "lead_capture")).isTrue();
    }

    @Test
    void isEnabledReturnsFalseWhenExplicitlyDisabled() {
        FeatureGate gate = new FeatureGate();
        gate.setSiteId("site-1");
        gate.setGateKey("realtime_collab");
        gate.setEnabled(false);
        when(featureGateMapper.getBySiteAndKey("site-1", "realtime_collab")).thenReturn(gate);
        assertThat(service.isEnabled("site-1", "realtime_collab")).isFalse();
    }

    @Test
    void isEnabledReturnsTrueWhenExplicitlyEnabled() {
        FeatureGate gate = new FeatureGate();
        gate.setEnabled(true);
        when(featureGateMapper.getBySiteAndKey("site-1", "poster_export")).thenReturn(gate);
        assertThat(service.isEnabled("site-1", "poster_export")).isTrue();
    }

    @Test
    void isEnabledNullArgsDefaultTrue() {
        assertThat(service.isEnabled(null, "lead_capture")).isTrue();
        assertThat(service.isEnabled("site-1", null)).isTrue();
    }

    @Test
    void setEnabledUpsertsGate() {
        boolean result = service.setEnabled("site-1", "page_versioning", false);

        assertThat(result).isFalse();
        ArgumentCaptor<FeatureGate> captor = ArgumentCaptor.forClass(FeatureGate.class);
        verify(featureGateMapper).upsert(captor.capture());
        FeatureGate saved = captor.getValue();
        assertThat(saved.getSiteId()).isEqualTo("site-1");
        assertThat(saved.getGateKey()).isEqualTo("page_versioning");
        assertThat(saved.isEnabled()).isFalse();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void knownKeysContainsExpectedGates() {
        assertThat(FeatureGateService.KNOWN_KEYS).contains(
                "lead_capture", "realtime_collab", "page_versioning", "poster_export",
                "analytics", "ab_testing");
    }
}
