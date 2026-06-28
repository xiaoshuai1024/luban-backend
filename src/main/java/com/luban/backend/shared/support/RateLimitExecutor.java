package com.luban.backend.shared.support;
import com.luban.backend.shared.support.AntiSpamService;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 滑动窗口频控的执行器：封装对 Redis 的 Lua 脚本调用。
 *
 * <p>抽出此层的两个目的：
 * <ol>
 *   <li>把 Redis 交互从 {@link AntiSpamService} 业务逻辑中隔离，{@code AntiSpamService}
 *       只负责参数校验与阈值判断，{@link RateLimitExecutor} 负责 Redis 交互；</li>
 *   <li>避开在 JDK 23 + Mockito inline mock 下 mock {@link StringRedisTemplate}（final-ish
 *       类）的限制——单测可直接 mock 本接口。</li>
 * </ol>
 */
@Component
public class RateLimitExecutor {

    private final StringRedisTemplate redis;

    public RateLimitExecutor(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Lua：滑动窗口原子操作。
     * KEYS[1] = 频控 key（ZSET）
     * ARGV[1] = windowStart（毫秒，清理阈值）
     * ARGV[2] = now（毫秒，当前成员分值）
     * ARGV[3] = member（唯一成员名）
     * ARGV[4] = windowSeconds（用于首次设置 TTL，防止 ZSET 永驻）
     */
    private static final DefaultRedisScript<Long> SLIDING_WINDOW_SCRIPT;
    static {
        SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>();
        SLIDING_WINDOW_SCRIPT.setScriptText(
                "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1]) " +
                "redis.call('ZADD', KEYS[1], ARGV[2], ARGV[3]) " +
                "local c = redis.call('ZCARD', KEYS[1]) " +
                // 仅在首次（TTL 未设置）时设置过期，避免每次重置导致永不过期窗口。
                "if redis.call('TTL', KEYS[1]) < 0 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[4])) end " +
                "return c"
        );
        SLIDING_WINDOW_SCRIPT.setResultType(Long.class);
    }

    /**
     * 执行滑动窗口计数。返回窗口内当前成员数（含本次请求）。
     *
     * @param key           ZSET key
     * @param windowStartMs 清理阈值（毫秒，早于此分值的成员会被移除）
     * @param nowMs         当前请求时间戳（毫秒）
     * @param member        唯一成员名
     * @param windowSeconds 窗口秒数（用于首次设置 TTL）
     * @return 窗口内成员数；Redis 不可用时返回 0（视为不限流）
     */
    public long countInWindow(String key, long windowStartMs, long nowMs, String member, int windowSeconds) {
        Long count = redis.execute(
                SLIDING_WINDOW_SCRIPT,
                List.of(key),
                String.valueOf(windowStartMs),
                String.valueOf(nowMs),
                member,
                String.valueOf(windowSeconds)
        );
        return count == null ? 0L : count;
    }
}
