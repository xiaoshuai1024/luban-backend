package com.luban.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Lead 敏感字段（phone/email）AES-GCM 加密。纯逻辑可单测。
 *
 * 输出格式：base64( IV(12B) || ciphertext+tag )。
 * 密钥来自 LEAD_ENC_KEY（32 字节 base64）；未配置时使用开发兜底 key（生产必须配置）。
 */
@Service
public class LeadCryptoService {

    private static final Logger log = LoggerFactory.getLogger(LeadCryptoService.class);
    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    /** 开发兜底 key（32 字节，全 0x42）；生产必须用 LEAD_ENC_KEY 覆盖。 */
    private static final byte[] DEV_FALLBACK_KEY = new byte[32];

    static {
        for (int i = 0; i < DEV_FALLBACK_KEY.length; i++) DEV_FALLBACK_KEY[i] = 0x42;
    }

    private final SecretKeySpec keySpec;
    private final SecureRandom random = new SecureRandom();

    public LeadCryptoService(@Value("${lead.enc.key:}") String keyB64) {
        this.keySpec = new SecretKeySpec(deriveKey(keyB64), "AES");
    }

    /**
     * T-be-8 安全加固：密钥强制 env（无默认值）。
     * 生产环境（profile=prod）若未配置 LEAD_ENC_KEY，启动直接失败。
     * 开发环境保留兜底 key + WARN 日志。
     */
    private static byte[] deriveKey(String keyB64) {
        if (keyB64 == null || keyB64.isBlank()) {
            String profile = System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", "");
            if ("prod".equalsIgnoreCase(profile)) {
                // 生产环境强制要求 LEAD_ENC_KEY，无默认值（plan §6.1b 脱敏红线）
                throw new IllegalStateException(
                    "生产环境必须配置 LEAD_ENC_KEY（lead.enc.key）；禁止使用开发兜底 key");
            }
            log.warn("LEAD_ENC_KEY 未配置，使用开发兜底 key；生产环境必须配置 lead.enc.key");
            return DEV_FALLBACK_KEY;
        }
        byte[] k = Base64.getDecoder().decode(keyB64);
        if (k.length != 32 && k.length != 16) {
            throw new IllegalArgumentException("LEAD_ENC_KEY 须为 16 或 32 字节 base64，实际 " + k.length);
        }
        if (k.length == 16) {
            byte[] k32 = new byte[32];
            System.arraycopy(k, 0, k32, 0, 16);
            return k32;
        }
        return k;
    }

    /** 加密明文，返回 base64(IV||ciphertext+tag)。 */
    public String encrypt(String plain) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    /** 解密 base64(IV||ciphertext+tag)，返回明文。 */
    public String decrypt(String cipherB64) {
        if (cipherB64 == null) return null;
        try {
            byte[] all = Base64.getDecoder().decode(cipherB64);
            if (all.length <= IV_LEN) {
                throw new IllegalArgumentException("密文过短");
            }
            byte[] iv = new byte[IV_LEN];
            byte[] ct = new byte[all.length - IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            System.arraycopy(all, IV_LEN, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("解密失败", e);
        }
    }

    /** 手机号脱敏：138****1234。 */
    public String maskPhone(String phone) {
        if (phone == null) return null;
        String s = phone.replaceAll("\\D", "");
        if (s.length() < 7) return "***";
        return s.substring(0, 3) + "****" + s.substring(s.length() - 4);
    }

    /** 邮箱脱敏：a***@b.com。 */
    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String masked = name.length() <= 1 ? "***" : name.substring(0, 1) + "***";
        return masked + "@" + parts[1];
    }
}
