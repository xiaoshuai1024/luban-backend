package com.luban.backend.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 留资防刷：基于 Redis 的固定窗口频控（维度 IP + formId）+ 验证码占位。
 * 纯逻辑通过 mock StringRedisTemplate 单测覆盖，不依赖 Redis 实际运行。
 */
@Service
public class AntiSpamService {

    private final StringRedisTemplate redis;

    public AntiSpamService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 固定窗口频控。返回 true 表示已达阈值，应拒绝（LEAD_SPAM_BLOCKED）。
     * 用 Lua 脚本保证 INCR + EXPIRE 原子性（🟡 修复 lost-expiry race）。
     *
     * @param ip             客户端 IP（X-Forwarded-For）
     * @param formId         表单 ID
     * @param max            窗口内最大允许次数
     * @param windowSeconds  窗口秒数
     */
    public boolean isRateLimited(String ip, String formId, int max, int windowSeconds) {
        if (ip == null || ip.isBlank() || formId == null || max <= 0) {
            return false;
        }
        String key = key(ip, formId);
        // 原子 INCR + 仅首次 EXPIRE（Lua 脚本，避免进程崩溃导致计数 key 永不过期 → 永久限流）
        Long count = redis.execute(
                RATE_LIMIT_SCRIPT,
                List.of(key),
                String.valueOf(windowSeconds)
        );
        return count != null && count > max;
    }

    /** Lua：原子 INCR + 仅首次 EXPIRE。 */
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;
    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setScriptText(
                "local c = redis.call('INCR', KEYS[1]) " +
                "if c == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end " +
                "return c"
        );
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
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
