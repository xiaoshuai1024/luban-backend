package com.luban.backend.shared.domain;

import com.luban.backend.shared.domain.event.CampaignActivatedEvent;
import com.luban.backend.shared.entity.Campaign;
import com.luban.backend.shared.entity.Channel;
import com.luban.backend.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CampaignAggregate 单测（backend-ddd-refactor plan v2 T10，重写静态类为真聚合根）。
 *
 * <p>锁定真聚合根范式不变量：
 * <ul>
 *   <li>工厂 newCampaign：初始 status=planned，时间窗 + name 校验</li>
 *   <li>transition：状态机 planned→active→completed/cancelled，非法转换抛异常</li>
 *   <li>update：patch 语义 + 时间窗校验</li>
 *   <li>assertDeletable：有 channel 抛 CAMPAIGN_HAS_CHANNELS</li>
 * </ul>
 *
 * <p>无状态静态工具（validateAndResolveCode/newChannel/transitionChannel）保留——合法纯函数。
 */
class CampaignAggregateTest {

    @Test
    void newCampaignDefaultsToPlannedStatus() {
        Instant before = Instant.now();
        CampaignAggregate agg = CampaignAggregate.newCampaign(
                "c-1", "site-1", "春节活动", null, null);

        Campaign c = agg.toCampaign();
        assertThat(c.getStatus()).isEqualTo("planned");
        assertThat(c.getCreatedAt()).isBetween(before, Instant.now().plusSeconds(1));
        assertThat(agg.channels()).isEmpty();
        assertThat(agg.pullEvents()).isEmpty();
    }

    @Test
    void newCampaignRejectsBlankName() {
        assertThatThrownBy(() -> CampaignAggregate.newCampaign(
                "c-1", "site-1", "  ", null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("MISSING_FIELD");
    }

    @Test
    void newCampaignRejectsInvalidTimeWindow() {
        Instant start = Instant.parse("2026-02-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-01T00:00:00Z");
        assertThatThrownBy(() -> CampaignAggregate.newCampaign(
                "c-1", "site-1", "活动", start, end))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("INVALID_TIME_WINDOW");
    }

    @Test
    void reconstitutePreservesStateWithChannels() {
        Campaign persisted = new Campaign();
        persisted.setId("c-9");
        persisted.setStatus("active");
        Channel ch = new Channel();
        ch.setId("ch-1");
        ch.setCampaignId("c-9");

        CampaignAggregate agg = CampaignAggregate.reconstitute(persisted, List.of(ch));

        assertThat(agg.toCampaign().getStatus()).isEqualTo("active");
        assertThat(agg.channels()).hasSize(1);
    }

    @Test
    void updatePatchesFields() {
        CampaignAggregate agg = CampaignAggregate.newCampaign(
                "c-1", "site-1", "原名", null, null);
        Instant start = Instant.parse("2026-02-01T00:00:00Z");

        agg.update("新名", start, null);

        assertThat(agg.toCampaign().getName()).isEqualTo("新名");
        assertThat(agg.toCampaign().getStartAt()).isEqualTo(start);
    }

    @Test
    void updateRejectsInvalidTimeWindow() {
        CampaignAggregate agg = CampaignAggregate.newCampaign(
                "c-1", "site-1", "活动", null, null);

        assertThatThrownBy(() -> agg.update("活动",
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("INVALID_TIME_WINDOW");
    }

    @Test
    void transitionPlannedToActiveSucceeds() {
        CampaignAggregate agg = CampaignAggregate.newCampaign(
                "c-1", "site-1", "活动", null, null);
        agg.transition("active");
        assertThat(agg.toCampaign().getStatus()).isEqualTo("active");
        // G1 补：转换发 CampaignActivatedEvent（含 from/to）
        assertThat(agg.pullEvents())
                .singleElement()
                .isInstanceOf(CampaignActivatedEvent.class);
    }

    @Test
    void transitionActiveToCompletedSucceeds() {
        Campaign persisted = new Campaign();
        persisted.setStatus("active");
        CampaignAggregate agg = CampaignAggregate.reconstitute(persisted, List.of());
        agg.transition("completed");
        assertThat(agg.toCampaign().getStatus()).isEqualTo("completed");
    }

    @Test
    void transitionActiveToCancelledSucceeds() {
        // G1 补：活动运行中取消（active→cancelled 合法转换，原 transition matrix 缺此用例）
        Campaign persisted = new Campaign();
        persisted.setStatus("active");
        CampaignAggregate agg = CampaignAggregate.reconstitute(persisted, List.of());

        agg.transition("cancelled");

        assertThat(agg.toCampaign().getStatus()).isEqualTo("cancelled");
        assertThat(agg.pullEvents())
                .singleElement()
                .isInstanceOf(CampaignActivatedEvent.class);
    }

    @Test
    void transitionPlannedToCancelledDirectlySucceeds() {
        CampaignAggregate agg = CampaignAggregate.newCampaign(
                "c-1", "site-1", "活动", null, null);
        agg.transition("cancelled");
        assertThat(agg.toCampaign().getStatus()).isEqualTo("cancelled");
    }

    @Test
    void transitionPlannedToCompletedRejected() {
        CampaignAggregate agg = CampaignAggregate.newCampaign(
                "c-1", "site-1", "活动", null, null);
        assertThatThrownBy(() -> agg.transition("completed"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("INVALID_STATE_TRANSITION");
    }

    @Test
    void assertDeletablePassesWhenNoChannels() {
        CampaignAggregate agg = CampaignAggregate.newCampaign(
                "c-1", "site-1", "活动", null, null);
        agg.assertDeletable(false);
    }

    @Test
    void assertDeletableThrowsWhenHasChannels() {
        CampaignAggregate agg = CampaignAggregate.newCampaign(
                "c-1", "site-1", "活动", null, null);
        assertThatThrownBy(() -> agg.assertDeletable(true))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("CAMPAIGN_HAS_CHANNELS");
    }

    @Test
    void pullEventsDrains() {
        CampaignAggregate agg = CampaignAggregate.newCampaign(
                "c-1", "site-1", "活动", null, null);
        assertThat(agg.pullEvents()).isEmpty();
        assertThat(agg.pullEvents()).isEmpty();
    }
}
