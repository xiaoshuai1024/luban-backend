package com.luban.backend.shared.support;
import com.luban.backend.shared.support.DedupService;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DedupService 单测：覆盖指纹稳定性、键顺序无关性、默认键、各策略决策分支。
 * 纯逻辑，不依赖 MySQL/Redis。
 */
class DedupServiceTest {

    private final DedupService dedup = new DedupService();

    // ---- computeHash ----

    @Test
    void sameContactProducesSameHash() {
        Map<String, String> c = Map.of("phone", "13800000000", "email", "a@b.com");
        String h1 = dedup.computeHash("form-1", c, List.of("phone", "email"));
        String h2 = dedup.computeHash("form-1", c, List.of("phone", "email"));
        assertThat(h1).isEqualTo(h2);
        assertThat(h1).hasSize(64); // sha256 hex
    }

    @Test
    void differentPhoneProducesDifferentHash() {
        Map<String, String> c1 = Map.of("phone", "13800000000");
        Map<String, String> c2 = Map.of("phone", "13900000000");
        assertThat(dedup.computeHash("form-1", c1, List.of("phone")))
                .isNotEqualTo(dedup.computeHash("form-1", c2, List.of("phone")));
    }

    @Test
    void differentFormIdProducesDifferentHash() {
        Map<String, String> c = Map.of("phone", "13800000000");
        assertThat(dedup.computeHash("form-1", c, List.of("phone")))
                .isNotEqualTo(dedup.computeHash("form-2", c, List.of("phone")));
    }

    @Test
    void dedupKeyOrderDoesNotAffectHash() {
        Map<String, String> c = Map.of("phone", "138", "email", "a@b.com");
        String h1 = dedup.computeHash("form-1", c, List.of("phone", "email"));
        String h2 = dedup.computeHash("form-1", c, List.of("email", "phone"));
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void missingDedupKeysDefaultsToPhone() {
        Map<String, String> c = Map.of("phone", "138", "email", "a@b.com");
        String explicit = dedup.computeHash("form-1", c, List.of("phone"));
        String defaulted = dedup.computeHash("form-1", c, null);
        assertThat(defaulted).isEqualTo(explicit);
    }

    @Test
    void nullContactProducesStableHash() {
        String h1 = dedup.computeHash("form-1", null, List.of("phone"));
        String h2 = dedup.computeHash("form-1", null, List.of("phone"));
        assertThat(h1).isEqualTo(h2);
    }

    // ---- decide ----

    @Test
    void acceptWhenNotExistsRegardlessOfPolicy() {
        for (DedupService.Policy p : DedupService.Policy.values()) {
            assertThat(dedup.decide(false, p)).isEqualTo(DedupService.Decision.ACCEPT);
        }
    }

    @Test
    void rejectPolicyRejectsDuplicate() {
        assertThat(dedup.decide(true, DedupService.Policy.REJECT))
                .isEqualTo(DedupService.Decision.REJECT);
    }

    @Test
    void markPolicyMarksDuplicate() {
        assertThat(dedup.decide(true, DedupService.Policy.MARK))
                .isEqualTo(DedupService.Decision.MARK_DUPLICATE);
    }

    @Test
    void overwriteAndMergeAcceptDuplicate() {
        assertThat(dedup.decide(true, DedupService.Policy.OVERWRITE))
                .isEqualTo(DedupService.Decision.ACCEPT);
        assertThat(dedup.decide(true, DedupService.Policy.MERGE))
                .isEqualTo(DedupService.Decision.ACCEPT);
    }
}
