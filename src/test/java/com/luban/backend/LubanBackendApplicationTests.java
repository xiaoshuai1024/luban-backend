package com.luban.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test: Spring context loads under the test profile, Flyway migrations
 * run successfully against the test database, and the application wiring is valid.
 */
@SpringBootTest
@ActiveProfiles("test")
class LubanBackendApplicationTests {

    @Test
    void contextLoads() {
        // If the context fails to load (e.g. Flyway migration error, bean wiring
        // issue, datasource misconfiguration), this test fails fast.
        assertThat(true).isTrue();
    }
}
