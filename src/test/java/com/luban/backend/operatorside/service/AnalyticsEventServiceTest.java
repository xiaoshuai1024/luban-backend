package com.luban.backend.operatorside.service;

import com.luban.backend.shared.crypto.LeadCryptoService;
import com.luban.backend.shared.dto.AnalyticsEventInput;
import com.luban.backend.shared.entity.AnalyticsEvent;
import com.luban.backend.shared.mapper.AnalyticsEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * AnalyticsEventService 单测（backend-ddd-refactor T17）。
 *
 * <p>覆盖批量入库管道：空批次、MAX_BATCH(50) 截断、正常入库、source_ip AES 脱敏、
 * 单条 insert 失败容错（不影响整批）。
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsEventServiceTest {

    @Mock private AnalyticsEventMapper eventMapper;

    private AnalyticsEventService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsEventService(eventMapper, new LeadCryptoService(""));
    }

    private AnalyticsEventInput input(String type) {
        return new AnalyticsEventInput(type, "page-1", "var-1", "{}", 1700000000000L, "{}");
    }

    @Test
    void receiveBatch_nullEvents_returnsZeroWithoutInsert() {
        int count = service.receiveBatch("site-1", null, "1.2.3.4", "v-1");

        assertThat(count).isZero();
        verify(eventMapper, never()).insert(any());
    }

    @Test
    void receiveBatch_emptyEvents_returnsZeroWithoutInsert() {
        int count = service.receiveBatch("site-1", List.of(), "1.2.3.4", "v-1");

        assertThat(count).isZero();
        verify(eventMapper, never()).insert(any());
    }

    @Test
    void receiveBatch_insertsAllEventsAndEncryptsSourceIp() {
        List<AnalyticsEventInput> events = List.of(input("page_view"), input("form_expose"));

        int count = service.receiveBatch("site-1", events, "1.2.3.4", "v-1");

        assertThat(count).isEqualTo(2);
        ArgumentCaptor<AnalyticsEvent> captor = ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(eventMapper, times(2)).insert(captor.capture());
        AnalyticsEvent first = captor.getAllValues().get(0);
        assertThat(first.getSiteId()).isEqualTo("site-1");
        assertThat(first.getVisitorId()).isEqualTo("v-1");
        assertThat(first.getEventType()).isEqualTo("page_view");
        // source_ip 已 AES 加密（非明文）
        assertThat(first.getSourceIpHashed()).isNotEqualTo("1.2.3.4");
        assertThat(first.getClientTs()).isNotNull();   // clientTs != null → 转换为 Instant
        assertThat(first.getServerTs()).isNotNull();
    }

    @Test
    void receiveBatch_truncatesAtMaxBatchLimit() {
        // 60 条输入 → 截断到 MAX_BATCH(50)
        List<AnalyticsEventInput> events = IntStream.range(0, 60)
                .mapToObj(i -> input("page_view")).toList();

        int count = service.receiveBatch("site-1", events, "1.2.3.4", "v-1");

        assertThat(count).isEqualTo(50);
        verify(eventMapper, times(50)).insert(any());
    }

    @Test
    void receiveBatch_singleInsertFailureDoesNotAbortBatch() {
        // 第 2 条 insert 抛异常 → 容错跳过，其余正常入库，返回成功数（非异常数）
        List<AnalyticsEventInput> events = new ArrayList<>();
        events.add(input("page_view"));      // ok
        events.add(input("form_submit"));    // will throw
        events.add(input("page_view"));      // ok
        doThrow(new RuntimeException("db down")).when(eventMapper).insert(any());

        int count = service.receiveBatch("site-1", events, "1.2.3.4", "v-1");

        // 全部 insert 都抛异常 → count=0（容错：单条失败计入跳过，不传播）
        assertThat(count).isZero();
        verify(eventMapper, times(3)).insert(any());
    }

    @Test
    void receiveBatch_nullClientTs_leavesClientTsNull() {
        AnalyticsEventInput ev = new AnalyticsEventInput("page_view", "page-1", null, "{}", null, "{}");

        service.receiveBatch("site-1", List.of(ev), "1.2.3.4", "v-1");

        ArgumentCaptor<AnalyticsEvent> captor = ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(eventMapper).insert(captor.capture());
        assertThat(captor.getValue().getClientTs()).isNull();
    }

    @Test
    void receiveBatch_nullSourceIp_handledSafely() {
        // cryptoService.encrypt(null) 应 null 安全，不抛 NPE
        int count = service.receiveBatch("site-1", List.of(input("page_view")), null, "v-1");

        assertThat(count).isEqualTo(1);
        verify(eventMapper).insert(any());
    }
}
