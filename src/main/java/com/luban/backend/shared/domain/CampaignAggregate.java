package com.luban.backend.shared.domain;

import com.luban.backend.shared.entity.Campaign;
import com.luban.backend.shared.entity.Channel;
import com.luban.backend.shared.exception.BusinessException;

import java.util.UUID;

/**
 * Campaign 聚合根（app-deeplink-backend-arch plan T7）。
 *
 * <p>封装 Campaign + Channel 的领域不变量与状态机。聚合外的代码（controller/service）
 * 通过聚合根操作 Channel，不直接改 Channel 实体。
 *
 * <p><b>不变量</b>：
 * <ul>
 *   <li>channel.siteId 必须 = campaign.siteId（若 channel 挂活动）</li>
 *   <li>channel.code 非空且符合 [a-zA-Z0-9_-]{1,32}（短码格式）</li>
 *   <li>channel.shortUrl = channel.code（短链路径即短码）</li>
 *   <li>campaign 时间窗 endAt >= startAt（若都提供）</li>
 * </ul>
 *
 * <p><b>状态机</b>：
 * <ul>
 *   <li>Campaign: planned → active → completed/cancelled</li>
 *   <li>Channel: active ↔ inactive</li>
 * </ul>
 *
 * <p>纯领域类，不依赖 Spring；由 operatorside 的 ChannelService/CampaignService 使用。
 */
public class CampaignAggregate {

    /** Channel 短码格式白名单（防恶意注入，对齐 plan 敏感字段清单） */
    public static final String CODE_PATTERN = "^[a-zA-Z0-9_-]{1,32}$";

    /** Channel 类型枚举 */
    public static final class ChannelType {
        public static final String QRCODE = "qrcode";
        public static final String H5 = "h5";
        public static final String SOCIAL = "social";
        public static final String AD = "ad";
        public static final String MINIAPP = "miniapp";
    }

    // ===== Campaign 状态机 =====

    /**
     * Campaign 状态转换。非法转换抛 BusinessException。
     * planned → active → completed/cancelled；active → cancelled。
     */
    public static void transitionCampaign(Campaign campaign, String targetStatus) {
        String current = campaign.getStatus();
        boolean valid =
                ("planned".equals(current) && "active".equals(targetStatus)) ||
                ("active".equals(current) && "completed".equals(targetStatus)) ||
                ("active".equals(current) && "cancelled".equals(targetStatus)) ||
                ("planned".equals(current) && "cancelled".equals(targetStatus));
        if (!valid) {
            throw BusinessException.invalidStateTransition(current, targetStatus);
        }
        campaign.setStatus(targetStatus);
    }

    /**
     * 校验 Campaign 创建参数。endAt >= startAt（若都提供）。
     */
    public static void validateCampaignCreate(String name, java.time.Instant startAt, java.time.Instant endAt) {
        if (name == null || name.isBlank()) {
            throw BusinessException.missingField("name");
        }
        if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
            throw BusinessException.invalidTimeWindow();
        }
    }

    // ===== Channel 状态机 =====

    /**
     * Channel 状态转换。active ↔ inactive；其他转换拒绝。
     */
    public static void transitionChannel(Channel channel, String targetStatus) {
        String current = channel.getStatus();
        boolean valid =
                ("active".equals(current) && "inactive".equals(targetStatus)) ||
                ("inactive".equals(current) && "active".equals(targetStatus));
        if (!valid) {
            throw BusinessException.invalidStateTransition(current, targetStatus);
        }
        channel.setStatus(targetStatus);
    }

    /**
     * 校验 Channel 创建参数 + 生成短码/shortUrl。
     *
     * @param siteId        站点 ID
     * @param code          运营指定的短码（null 则系统生成 base62）
     * @param targetPageId  目标页面 ID
     * @param pageSiteId    目标页面实际所属站点 ID（用于校验归属）
     * @return 最终短码（= shortUrl）
     */
    public static String validateAndResolveCode(String siteId, String code, String targetPageId, String pageSiteId) {
        // page 归属校验（防开放重定向，OWASP A08）
        if (targetPageId == null || targetPageId.isBlank()) {
            throw BusinessException.missingField("targetPageId");
        }
        if (!siteId.equals(pageSiteId)) {
            throw BusinessException.pageNotBelongToSite();
        }
        // code：运营指定 or 系统生成
        String resolved;
        if (code == null || code.isBlank()) {
            resolved = generateBase62Code();
        } else {
            if (!code.matches(CODE_PATTERN)) {
                throw BusinessException.invalidCodeFormat();
            }
            resolved = code;
        }
        return resolved;
    }

    /**
     * 构建待持久化的 Channel 实体（设置 id/默认状态/shortUrl=code）。
     */
    public static Channel newChannel(String siteId, String campaignId, String code, String type,
                                     String utmTemplate, String targetPageId) {
        Channel ch = new Channel();
        ch.setId(UUID.randomUUID().toString());
        ch.setSiteId(siteId);
        ch.setCampaignId(campaignId);
        ch.setCode(code);
        ch.setShortUrl(code); // shortUrl = code（plan 决策 2）
        ch.setType(type);
        ch.setUtmTemplate(utmTemplate);
        ch.setTargetPageId(targetPageId);
        ch.setStatus("active");
        return ch;
    }

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final java.security.SecureRandom SECURE_RNG = new java.security.SecureRandom();

    /**
     * base62 短码生成（6 位，[0-9a-zA-Z]）。
     * 用 SecureRandom 逐位独立均匀采样，消除偏置 + 避免 Math.abs(Long.MIN_VALUE) 边界 bug。
     * 碰撞由 DB 唯一约束（uk_short_url + uk_site_code）兜底，调用方捕获重试。
     */
    private static String generateBase62Code() {
        char[] buf = new char[6];
        for (int i = 0; i < 6; i++) {
            buf[i] = ALPHABET.charAt(SECURE_RNG.nextInt(62));
        }
        return new String(buf);
    }
}
