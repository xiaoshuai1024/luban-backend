package com.luban.backend.shared;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PingController 单测（健康检查端点契约）。backend-ddd-refactor T17 补覆盖率。
 * 纯方法调用（不起 ApplicationContext）——健康检查无依赖，直接断言返回体。
 */
class PingControllerTest {

    private final PingController controller = new PingController();

    @Test
    void ping_returnsOkWithPongMessage() {
        ResponseEntity<Map<String, String>> resp = controller.ping();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsEntry("message", "pong");
    }

    @Test
    void healthz_returnsOkWithOkStatus() {
        ResponseEntity<Map<String, String>> resp = controller.healthz();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsEntry("status", "ok");
    }
}
