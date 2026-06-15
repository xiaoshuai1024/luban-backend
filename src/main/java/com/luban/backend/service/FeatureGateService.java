package com.luban.backend.service;

import com.luban.backend.entity.FeatureGate;
import com.luban.backend.mapper.FeatureGateMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

/**
 * 特性开关服务（plan §3.5）。
 * <p>已知开关：lead_capture / realtime_collab / page_versioning / poster_export。
 * <p>未配置的开关默认开启（enabled=true）；显式关闭后才降级。
 */
@Service
public class FeatureGateService {

    /** 已注册的特性开关 key（防拼写错误）。 */
    public static final Set<String> KNOWN_KEYS = Set.of(
            "lead_capture", "realtime_collab", "page_versioning", "poster_export");

    private final FeatureGateMapper featureGateMapper;

    public FeatureGateService(FeatureGateMapper featureGateMapper) {
        this.featureGateMapper = featureGateMapper;
    }

    /**
     * 读取开关状态（默认开启）。
     *
     * @param siteId  站点 ID
     * @param gateKey 开关 key
     * @return true=开启（默认），false=显式关闭
     */
    public boolean isEnabled(String siteId, String gateKey) {
        if (siteId == null || gateKey == null) return true;
        FeatureGate gate = featureGateMapper.getBySiteAndKey(siteId, gateKey);
        return gate == null || gate.isEnabled();
    }

    /**
     * 设置开关状态（upsert）。未知 key 仍可写入（便于扩展）。
     */
    public boolean setEnabled(String siteId, String gateKey, boolean enabled) {
        FeatureGate gate = new FeatureGate();
        gate.setSiteId(siteId);
        gate.setGateKey(gateKey);
        gate.setEnabled(enabled);
        gate.setUpdatedAt(Instant.now());
        featureGateMapper.upsert(gate);
        return enabled;
    }
}
