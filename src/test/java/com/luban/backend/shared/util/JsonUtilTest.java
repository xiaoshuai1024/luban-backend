package com.luban.backend.shared.util;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JsonUtil 单测（G1 补：覆盖率门禁）。
 */
class JsonUtilTest {

    @Test
    void toString_serializesJsonNode() {
        ObjectNode node = JsonUtil.MAPPER.createObjectNode();
        node.put("k", "v");

        assertThat(JsonUtil.toString(node)).isEqualTo("{\"k\":\"v\"}");
    }

    @Test
    void toString_returnsNullForNullInput() {
        assertThat(JsonUtil.toString(null)).isNull();
    }

    @Test
    void writeValueAsString_serializesObject() {
        assertThat(JsonUtil.writeValueAsString(new TestDto("a", 1)))
                .isEqualTo("{\"name\":\"a\",\"count\":1}");
    }

    @Test
    void writeValueAsString_returnsNullForNullInput() {
        assertThat(JsonUtil.writeValueAsString(null)).isNull();
    }

    @Test
    void writeValueAsString_returnsNullOnFailure() {
        // 自引用对象触发 Jackson 循环检测异常 → 宽松降级返回 null
        SelfRef ref = new SelfRef();
        ref.self = ref;
        assertThat(JsonUtil.writeValueAsString(ref)).isNull();
    }

    private record TestDto(String name, int count) {}

    private static class SelfRef {
        SelfRef self;
    }
}
