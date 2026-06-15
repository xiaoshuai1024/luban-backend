package com.luban.backend.service;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lead 敏感字段加密单测：roundtrip、IV 随机性、篡改检测、脱敏。
 */
class LeadCryptoServiceTest {

    private static String randomKeyB64() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return Base64.getEncoder().encodeToString(k);
    }

    private final LeadCryptoService crypto = new LeadCryptoService(randomKeyB64());

    @Test
    void encryptThenDecryptRoundtrip() {
        String plain = "13800001234";
        String cipher = crypto.encrypt(plain);
        assertThat(crypto.decrypt(cipher)).isEqualTo(plain);
    }

    @Test
    void samePlainProducesDifferentCipherDueToRandomIv() {
        String plain = "13800001234";
        String c1 = crypto.encrypt(plain);
        String c2 = crypto.encrypt(plain);
        assertThat(c1).isNotEqualTo(c2); // IV 随机
        assertThat(crypto.decrypt(c1)).isEqualTo(plain);
        assertThat(crypto.decrypt(c2)).isEqualTo(plain);
    }

    @Test
    void tamperedCipherFailsToDecrypt() {
        String cipher = crypto.encrypt("13800001234");
        byte[] raw = Base64.getDecoder().decode(cipher);
        raw[raw.length - 1] ^= 0x01; // 篡改末字节（tag）
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThatThrownBy(() -> crypto.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("解密失败");
    }

    @Test
    void differentKeysCannotDecryptEachOther() {
        LeadCryptoService a = new LeadCryptoService(randomKeyB64());
        LeadCryptoService b = new LeadCryptoService(randomKeyB64());
        String cipher = a.encrypt("secret");
        assertThatThrownBy(() -> b.decrypt(cipher))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nullSafe() {
        assertThat(crypto.encrypt(null)).isNull();
        assertThat(crypto.decrypt(null)).isNull();
    }

    @Test
    void maskPhoneWorks() {
        assertThat(crypto.maskPhone("13800001234")).isEqualTo("138****1234");
        assertThat(crypto.maskPhone("138-0000-1234")).isEqualTo("138****1234");
        assertThat(crypto.maskPhone("123")).isEqualTo("***");
        assertThat(crypto.maskPhone(null)).isNull();
    }

    @Test
    void maskEmailWorks() {
        assertThat(crypto.maskEmail("alice@example.com")).isEqualTo("a***@example.com");
        assertThat(crypto.maskEmail("a@b.com")).isEqualTo("***@b.com");
        assertThat(crypto.maskEmail("not-an-email")).isEqualTo("***");
    }

    @Test
    void invalidKeyLengthRejected() {
        // 10 字节，非法
        byte[] bad = new byte[10];
        String badB64 = Base64.getEncoder().encodeToString(bad);
        assertThatThrownBy(() -> new LeadCryptoService(badB64))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
