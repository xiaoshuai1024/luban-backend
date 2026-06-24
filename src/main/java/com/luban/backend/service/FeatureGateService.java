package com.luban.backend.service;

import com.luban.backend.entity.FeatureGate;
import com.luban.backend.entity.Subscription;
import com.luban.backend.mapper.FeatureGateMapper;
import com.luban.backend.mapper.SubscriptionMapper;
import com.luban.backend.mapper.UserSiteMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 特性开关服务（v01 §3.5 + v02 §3.2 plan 放行改造）。
 *
 * <p>已知开关：lead_capture / realtime_collab / page_versioning / poster_export / analytics / ab_testing。
 *
 * <p>v02 放行逻辑（向后兼容）：
 * <ol>
 *   <li>site 级显式关闭（enabled=false）→ 关闭（override 关闭，最高优先级）</li>
 *   <li>site 级开启或未配置 → 查 site owner 的 plan.gates：
 *     <ul>
 *       <li>plan.gates 含该 key → 开启</li>
 *       <li>plan.gates 不含该 key → 关闭（受 plan 限制，不可 override 开启）</li>
 *     </ul>
 *   </li>
 *   <li>无法确定 owner/plan（历史数据/无订阅）→ 默认开启（向后兼容 v01）</li>
 * </ol>
 *
 * <p>Free 档默认放行 lead_capture；Starter/Growth 档差异通过 QuotaInterceptor 实现而非 gate 卡。
 */
@Service
public class FeatureGateService {

    /** 已注册的特性开关 key（防拼写错误）。v02 新增 analytics/ab_testing。 */
    public static final Set<String> KNOWN_KEYS = Set.of(
            "lead_capture", "realtime_collab", "page_versioning", "poster_export",
            "analytics", "ab_testing");

    /** v01 旧 gate_key 集合（向后兼容：这些 Free 档默认放行）。 */
    private static final Set<String> V01_KEYS = Set.of(
            "lead_capture", "realtime_collab", "page_versioning", "poster_export");

    private final FeatureGateMapper featureGateMapper;
    private final UserSiteMapper userSiteMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final PlanService planService;

    public FeatureGateService(FeatureGateMapper featureGateMapper,
                              UserSiteMapper userSiteMapper,
                              SubscriptionMapper subscriptionMapper,
                              PlanService planService) {
        this.featureGateMapper = featureGateMapper;
        this.userSiteMapper = userSiteMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.planService = planService;
    }

    /**
     * 读取开关状态（v02 plan 放行，向后兼容）。
     *
     * @param siteId  站点 ID
     * @param gateKey 开关 key
     * @return true=开启，false=关闭（site 显式关 或 plan 不含）
     */
    public boolean isEnabled(String siteId, String gateKey) {
        if (siteId == null || gateKey == null) return true;

        // 1. site 级显式关闭 → 关闭（最高优先级）
        FeatureGate gate = featureGateMapper.getBySiteAndKey(siteId, gateKey);
        if (gate != null && !gate.isEnabled()) return false;

        // 2. site 未显式关闭 → 查 plan.gates 判定
        String ownerUserId = userSiteMapper.findOwnerUserId(siteId);
        if (ownerUserId == null) {
            // 无 owner 记录（历史站点）→ 向后兼容默认开启
            return gate == null || gate.isEnabled();
        }

        Subscription sub = subscriptionMapper.getByUserId(ownerUserId);
        if (sub == null) {
            // owner 无订阅记录 → 向后兼容默认开启
            return gate == null || gate.isEnabled();
        }

        List<String> planGates = planService.parseGates(
                planService.getPlan(sub.getPlanCode()) != null
                        ? planService.getPlan(sub.getPlanCode()).getGates()
                        : null);

        // plan.gates 含该 key → 开启
        if (planGates.contains(gateKey)) return true;

        // plan.gates 不含，但 v01 旧 key 且 site 未显式关 → 向后兼容默认开启
        // （避免 v01 用户的 lead_capture 等被 plan 卡住）
        if (V01_KEYS.contains(gateKey) && (gate == null || gate.isEnabled())) {
            return true;
        }

        // v02 新增 key（analytics/ab_testing）受 plan 严格限制，不在 plan.gates 则关闭
        return false;
    }

    /**
     * 设置开关状态（upsert）。未知 key 仍可写入（便于扩展）。
     * 注意：site 级只能 override 关闭，无法 override 开启被 plan 限制的 key。
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
