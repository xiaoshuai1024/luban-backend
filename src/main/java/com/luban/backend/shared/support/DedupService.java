package com.luban.backend.shared.support;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 留资去重服务（纯逻辑，无外部依赖，便于单测）。
 *
 * 计算 dedup_hash = sha256(formId + ":" + 排序后的去重键值)，并依据去重策略给出处置决策。
 */
@Service
public class DedupService {

    /** 去重命中时的处置策略 */
    public enum Policy { REJECT, MARK, OVERWRITE, MERGE }

    /** 去重判定后的处置决策 */
    public enum Decision { ACCEPT, REJECT, MARK_DUPLICATE }

    /**
     * 计算去重指纹。
     *
     * @param formId    表单 ID
     * @param contact   提交的联系人字段（phone/email/name...）
     * @param dedupKeys 去重键名列表；为空时默认 ["phone"]
     * @return sha256 hex（64 字符）
     */
    public String computeHash(String formId, Map<String, String> contact, List<String> dedupKeys) {
        List<String> keys = (dedupKeys == null || dedupKeys.isEmpty())
                ? List.of("phone") : dedupKeys;
        // 按 key 字典序排序，保证键顺序不影响指纹
        List<String> parts = keys.stream().sorted()
                .map(k -> contact == null ? "" : contact.getOrDefault(k, ""))
                .toList();
        String raw = formId + ":" + String.join("|", parts);
        return sha256Hex(raw);
    }

    /**
     * 根据时间窗内是否已存在相同指纹 + 策略，给出处置决策。
     *
     * @param existsInWindow 时间窗内是否已存在同指纹线索
     * @param policy         去重策略
     */
    public Decision decide(boolean existsInWindow, Policy policy) {
        if (!existsInWindow) {
            return Decision.ACCEPT;
        }
        return switch (policy) {
            case REJECT -> Decision.REJECT;
            case MARK -> Decision.MARK_DUPLICATE;
            case OVERWRITE, MERGE -> Decision.ACCEPT;
        };
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
