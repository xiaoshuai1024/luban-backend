package com.luban.backend.service;

import org.springframework.stereotype.Service;

/**
 * 留资防刷：基于 Redis 的<b>滑动窗口</b>频控（维度 IP + formId）+ 验证码占位。
 *
 * <p>业务逻辑：参数校验 + 阈值判断。Redis 交互委托给 {@link RateLimitExecutor}，
 * 单测只需 mock 该接口，绕开 mock {@code StringRedisTemplate}（JDK 23 限制）。
 *
 * <p>滑动窗口语义：每条请求以「成员=唯一 token、分值=时间戳」写入 ZSET，
 * 先清理窗口外的过期成员，再计数。相比固定窗口可更平滑地抵御边界突刺流量。
 */
@Service
public class AntiSpamService {

    private final RateLimitExecutor executor;

    public AntiSpamService(RateLimitExecutor executor) {
        this.executor = executor;
    }

    /**
     * 滑动窗口频控。返回 true 表示已达阈值，应拒绝（LEAD_SPAM_BLOCKED）。
     *
     * @param ip             客户端 IP（X-Forwarded-For）
     * @param formId         表单 ID
     * @param max            窗口内最大允许次数
     * @param windowSeconds  窗口秒数
     */
    public boolean isRateLimited(String ip, String formId, int max, int windowSeconds) {
        if (ip == null || ip.isBlank() || formId == null || max <= 0 || windowSeconds <= 0) {
            return false;
        }
        String key = key(ip, formId);
        long now = System.currentTimeMillis();
        long windowStart = now - windowSeconds * 1000L;
        // 成员唯一 token：时间戳 + 纳秒尾数，避免同毫秒请求相互覆盖。
        String member = now + ":" + System.nanoTime();
        long count = executor.countInWindow(key, windowStart, now, member, windowSeconds);
        return count > max;
    }

    /**
     * 图形验证码 token 校验（P0 占位：P0 默认不强制图形码，由 form 配置决定）。
     * 真实接入时替换为 captcha 服务校验。
     */
    public boolean verifyCaptcha(String token) {
        return true;
    }

    private static String key(String ip, String formId) {
        return "antispam:form:" + formId + ":ip:" + ip;
    }
}
