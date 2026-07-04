package com.luban.backend.shared.domain;

import com.luban.backend.shared.domain.event.LeadConvertedEvent;
import com.luban.backend.shared.entity.Lead;
import com.luban.backend.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LeadAggregate 单测（backend-ddd-refactor plan v2 T7，合并 LeadStatusMachine）。
 *
 * <p>锁定真聚合根范式不变量（替代 LeadStatusMachine + LeadStatusMachineTest）：
 * <ul>
 *   <li>状态机 NEW→ASSIGNED→CONTACTING→CONVERTED，终态 CONVERTED/INVALID/LOST</li>
 *   <li>非法转换抛 LEAD_INVALID_TRANSITION（409，对齐旧码）</li>
 *   <li>同状态幂等放行（NEW→NEW 不抛）</li>
 *   <li>parseStatus：null→NEW，大小写不敏感，未知抛 LEAD_INVALID_STATUS</li>
 *   <li>transit 副作用：CONVERTED 设 convertedAt，ASSIGNED 设 assigneeId</li>
 *   <li>CONVERTED 转换发 LeadConvertedEvent</li>
 * </ul>
 */
class LeadAggregateTest {

    @Test
    void validTransitionsAllowed() {
        assertThat(LeadAggregate.canTransit("new", "assigned")).isTrue();
        assertThat(LeadAggregate.canTransit("new", "invalid")).isTrue();
        assertThat(LeadAggregate.canTransit("assigned", "contacting")).isTrue();
        assertThat(LeadAggregate.canTransit("assigned", "invalid")).isTrue();
        assertThat(LeadAggregate.canTransit("assigned", "lost")).isTrue();
        assertThat(LeadAggregate.canTransit("contacting", "converted")).isTrue();
        assertThat(LeadAggregate.canTransit("contacting", "lost")).isTrue();
        assertThat(LeadAggregate.canTransit("contacting", "invalid")).isTrue();
    }

    @Test
    void invalidTransitionsRejected() {
        assertThat(LeadAggregate.canTransit("new", "contacting")).isFalse();
        assertThat(LeadAggregate.canTransit("new", "converted")).isFalse();
        assertThat(LeadAggregate.canTransit("new", "lost")).isFalse();
        assertThat(LeadAggregate.canTransit("assigned", "converted")).isFalse();
    }

    @Test
    void terminalStatesAllowNoTransition() {
        for (String terminal : new String[]{"converted", "invalid", "lost"}) {
            assertThat(LeadAggregate.canTransit(terminal, "new")).isFalse();
            assertThat(LeadAggregate.canTransit(terminal, "assigned")).isFalse();
        }
    }

    @Test
    void transitThrowsOnIllegalTransition() {
        LeadAggregate agg = LeadAggregate.reconstitute(leadWithStatus("new"));
        assertThatThrownBy(() -> agg.transit("contacting", "actor-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("LEAD_INVALID_TRANSITION");
    }

    @Test
    void sameStateIsIdempotent() {
        LeadAggregate agg = LeadAggregate.reconstitute(leadWithStatus("new"));
        agg.transit("new", null);   // 幂等不抛
    }

    @Test
    void parseStatusAcceptsCaseInsensitive() {
        assertThat(LeadAggregate.parseStatus("new")).isEqualTo("new");
        assertThat(LeadAggregate.parseStatus("Contacting")).isEqualTo("contacting");
        assertThat(LeadAggregate.parseStatus(null)).isEqualTo("new");
    }

    @Test
    void parseStatusRejectsUnknown() {
        assertThatThrownBy(() -> LeadAggregate.parseStatus("weird"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("LEAD_INVALID_STATUS");
    }

    @Test
    void transitToConvertedSetsConvertedAtAndEmitsEvent() {
        LeadAggregate agg = LeadAggregate.reconstitute(leadWithStatus("contacting"));

        agg.transit("converted", "actor-1");

        Lead l = agg.toLead();
        assertThat(l.getStatus()).isEqualTo("converted");
        assertThat(l.getConvertedAt()).isNotNull();
        var events = agg.pullEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(LeadConvertedEvent.class);
    }

    @Test
    void transitToAssignedSetsAssigneeWhenActorProvided() {
        LeadAggregate agg = LeadAggregate.reconstitute(leadWithStatus("new"));

        agg.transit("assigned", "sales-1");

        Lead l = agg.toLead();
        assertThat(l.getStatus()).isEqualTo("assigned");
        assertThat(l.getAssigneeId()).isEqualTo("sales-1");
    }

    @Test
    void transitToAssignedKeepsOldAssigneeWhenActorNull() {
        Lead lead = leadWithStatus("new");
        lead.setAssigneeId("old-sales");
        LeadAggregate agg = LeadAggregate.reconstitute(lead);

        agg.transit("assigned", null);

        assertThat(agg.toLead().getAssigneeId()).isEqualTo("old-sales");
    }

    @Test
    void transitToNonConvertedKeepsConvertedAt() {
        Lead lead = leadWithStatus("new");
        lead.setConvertedAt(Instant.parse("2026-01-01T00:00:00Z"));
        LeadAggregate agg = LeadAggregate.reconstitute(lead);

        agg.transit("assigned", "actor-1");

        assertThat(agg.toLead().getConvertedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void transitToLostDoesNotEmitConvertedEvent() {
        LeadAggregate agg = LeadAggregate.reconstitute(leadWithStatus("contacting"));

        agg.transit("lost", "actor-1");

        assertThat(agg.toLead().getStatus()).isEqualTo("lost");
        assertThat(agg.pullEvents()).isEmpty();
    }

    @Test
    void reconstitutePreservesStateWithoutEvents() {
        Lead lead = leadWithStatus("assigned");
        lead.setAssigneeId("u-1");
        LeadAggregate agg = LeadAggregate.reconstitute(lead);

        assertThat(agg.toLead().getAssigneeId()).isEqualTo("u-1");
        assertThat(agg.pullEvents()).isEmpty();
    }

    private static Lead leadWithStatus(String status) {
        Lead lead = new Lead();
        lead.setId("lead-1");
        lead.setSiteId("site-1");
        lead.setStatus(status);
        return lead;
    }
}
