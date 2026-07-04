package com.luban.backend.shared.domain;

import com.luban.backend.shared.entity.Channel;
import com.luban.backend.shared.exception.BusinessException;

import java.util.UUID;

/**
 * Channel 领域逻辑（backend-ddd-refactor plan v2 T10 / G1 修复 Y5）。
 *
 * <p><b>为什么独立于 {@link CampaignAggregate}</b>：Channel 是 Campaign 的子聚合（plan §10 明确
 * 不为它单独建聚合根，避免 scope 膨胀），但它的创建/状态机/短码生成是 <b>Channel 自身的领域逻辑</b>，
 * 不应寄生在 CampaignAggregate 上（那会让 CampaignAggregate 同时承载两个聚合的关注点，违反 SRP）。
 *
 * <p>本类是<b>无状态纯函数集合</b>（合法的领域 helper，非"静态工具类伪装聚合根"——后者指
 * 把本应是聚合根实例方法的状态/不变量逻辑写成静态方法）。Channel 的状态机/不变量在此，
 * CampaignAggregate 只持有 Channel 列表的只读视图。
 *
 * <p>纯 POJO，零框架依赖（domain 层纯净性）。
 */
public final class ChannelDomain {

    /** 短码格式：[a-zA-Z0-9_-]{1,32} */
    public static final String CODE_PATTERN = "^[a-zA-Z0-9_-]{1,32}$";

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final java.security.SecureRandom SECURE_RNG = new java.security.SecureRandom();

    private ChannelDomain() {}   // 工具类禁止实例化

    /**
     * 校验 Channel 创建参数 + 生成短码/shortUrl（无状态纯函数，独立 channel 与挂活动的 channel 共用）。
     */
    public static String validateAndResolveCode(String siteId, String code, String targetPageId, String pageSiteId) {
        if (targetPageId == null || targetPageId.isBlank()) {
            throw BusinessException.missingField("targetPageId");
        }
        if (!siteId.equals(pageSiteId)) {
            throw BusinessException.pageNotBelongToSite();
        }
        if (code == null || code.isBlank()) {
            return generateBase62Code();
        }
        if (!code.matches(CODE_PATTERN)) {
            throw BusinessException.invalidCodeFormat();
        }
        return code;
    }

    /**
     * Channel 状态转换（active↔inactive）。无状态纯函数。
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
     * 构建待持久化的 Channel 实体（设置 id/默认状态 active/shortUrl=code）。
     */
    public static Channel newChannel(String siteId, String campaignId, String code, String type,
                                     String utmTemplate, String targetPageId) {
        Channel ch = new Channel();
        ch.setId(UUID.randomUUID().toString());
        ch.setSiteId(siteId);
        ch.setCampaignId(campaignId);
        ch.setCode(code);
        ch.setShortUrl(code);
        ch.setType(type);
        ch.setUtmTemplate(utmTemplate);
        ch.setTargetPageId(targetPageId);
        ch.setStatus("active");
        return ch;
    }

    /**
     * base62 短码生成（6 位，[0-9a-zA-Z]）。碰撞由 DB 唯一约束兜底，调用方捕获重试。
     */
    public static String generateBase62Code() {
        char[] buf = new char[6];
        for (int i = 0; i < 6; i++) {
            buf[i] = ALPHABET.charAt(SECURE_RNG.nextInt(62));
        }
        return new String(buf);
    }
}
