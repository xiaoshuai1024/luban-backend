package com.luban.backend.shared.domain;

import com.luban.backend.shared.domain.event.CampaignActivatedEvent;
import com.luban.backend.shared.domain.event.DomainEvent;
import com.luban.backend.shared.entity.Campaign;
import com.luban.backend.shared.entity.Channel;
import com.luban.backend.shared.exception.BusinessException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Campaign 聚合根（backend-ddd-refactor plan v2 T10，重写静态工具类为真聚合根）。
 *
 * <p>v2 纠正（对齐 {@code .agents/rules/luban-engineering-principles.md} §2 反模式）：
 * 旧版 CampaignAggregate 是全 static 工具类（无状态、操作外部 entity）——属"静态工具类伪装聚合根"反模式。
 * 重写为真聚合根：final + 持有 Campaign 实体引用 + 充血实例方法 + 工厂 + pullEvents。
 *
 * <p><b>实例方法封装的不变量</b>（真聚合根职责）：
 * <ul>
 *   <li>工厂 newCampaign：初始 status=planned，时间窗校验 endAt&gt;=startAt</li>
 *   <li>transition：状态机 planned→active→completed/cancelled（planned→cancelled 允许直跳），
 *       非法转换抛 invalidStateTransition</li>
 *   <li>update：patch 语义 + 时间窗校验</li>
 *   <li>assertDeletable：有 channel 抛 CAMPAIGN_HAS_CHANNELS（跨聚合查询由 Service 传入 boolean）</li>
 * </ul>
 *
 * <p><b>保留为 static 的无状态工具</b>（合法领域工厂/纯函数，非反模式——反模式特指聚合根不变量写成
 * static 操作外部 entity，而非无状态纯函数）：
 * <ul>
 *   <li>{@link #CODE_PATTERN} / {@link ChannelType}：常量/枚举</li>
 *   <li>{@link #validateAndResolveCode}：channel 短码校验+生成（无状态，被独立 channel 也复用）</li>
 *   <li>{@link #newChannel}：channel 实体工厂；{@link #transitionChannel}：channel 状态机</li>
 * </ul>
 * 独立 channel（campaignId=null）的领域逻辑保留 static——channel 可作独立聚合根（plan 未要求
 * ChannelAggregate，此处不为它单独建聚合，避免 scope 膨胀）。
 */
public final class CampaignAggregate {

    /** Channel 类型枚举 */
    public static final class ChannelType {
        public static final String QRCODE = "qrcode";
        public static final String H5 = "h5";
        public static final String SOCIAL = "social";
        public static final String AD = "ad";
        public static final String MINIAPP = "miniapp";
    }

    // === 状态常量 ===
    public static final String STATUS_PLANNED = "planned";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_CANCELLED = "cancelled";

    private final Campaign root;
    private final List<Channel> channelList;
    private final List<DomainEvent> events = new ArrayList<>();

    private CampaignAggregate(Campaign root, List<Channel> channels) {
        this.root = root;
        this.channelList = channels != null ? new ArrayList<>(channels) : new ArrayList<>();
    }

    /** 工厂：创建新活动（初始 status=planned，时间窗校验）。 */
    public static CampaignAggregate newCampaign(String id, String siteId, String name,
                                                Instant startAt, Instant endAt) {
        validateCampaignCreate(name, startAt, endAt);
        Instant now = Instant.now();
        Campaign c = new Campaign();
        c.setId(id);
        c.setSiteId(siteId);
        c.setName(name);
        c.setStartAt(startAt);
        c.setEndAt(endAt);
        c.setStatus(STATUS_PLANNED);
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        return new CampaignAggregate(c, null);
    }

    /** 工厂：从持久化重建（含聚合内 channels）。 */
    public static CampaignAggregate reconstitute(Campaign persisted, List<Channel> channels) {
        return new CampaignAggregate(persisted, channels);
    }

    /** Patch 更新活动（null 字段保留原值，时间窗校验）。 */
    public void update(String name, Instant startAt, Instant endAt) {
        if (name != null) root.setName(name);
        if (startAt != null) root.setStartAt(startAt);
        if (endAt != null) root.setEndAt(endAt);
        Instant s = root.getStartAt();
        Instant e = root.getEndAt();
        if (s != null && e != null && e.isBefore(s)) {
            throw BusinessException.invalidTimeWindow();
        }
        root.setUpdatedAt(Instant.now());
    }

    /**
     * 状态转换（聚合根状态机）。非法转换抛 invalidStateTransition。
     * planned→active→completed/cancelled；planned→cancelled（直跳）；active→cancelled。
     */
    public void transition(String targetStatus) {
        String current = root.getStatus();
        boolean valid =
                (STATUS_PLANNED.equals(current) && STATUS_ACTIVE.equals(targetStatus)) ||
                (STATUS_ACTIVE.equals(current) && STATUS_COMPLETED.equals(targetStatus)) ||
                (STATUS_ACTIVE.equals(current) && STATUS_CANCELLED.equals(targetStatus)) ||
                (STATUS_PLANNED.equals(current) && STATUS_CANCELLED.equals(targetStatus));
        if (!valid) {
            throw BusinessException.invalidStateTransition(current, targetStatus);
        }
        root.setStatus(targetStatus);
        root.setUpdatedAt(Instant.now());
        // G1 补：状态机转换发事件（planned→active / active→completed / →cancelled）
        events.add(new CampaignActivatedEvent(root.getId(), root.getSiteId(), current, targetStatus, root.getUpdatedAt()));
    }

    /**
     * 断言可删除：有 channel 抛 CAMPAIGN_HAS_CHANNELS。
     * 跨聚合查询（channelMapper.listByCampaignId）由 Service 完成后传入 boolean。
     */
    public void assertDeletable(boolean hasChannels) {
        if (hasChannels) {
            throw BusinessException.campaignHasChannels();
        }
    }

    public Campaign toCampaign() {
        return root;
    }

    /** 聚合内 channels 视图（不可变）。 */
    public List<Channel> channels() {
        return List.copyOf(channelList);
    }

    public List<DomainEvent> pullEvents() {
        List<DomainEvent> drained = new ArrayList<>(events);
        events.clear();
        return drained;
    }

    // ===== 无状态静态工具（合法领域工厂/纯函数，非"静态类伪装聚合根"反模式） =====

    /**
     * 校验 Campaign 创建参数。endAt &gt;= startAt（若都提供）。
     */
    public static void validateCampaignCreate(String name, Instant startAt, Instant endAt) {
        if (name == null || name.isBlank()) {
            throw BusinessException.missingField("name");
        }
        if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
            throw BusinessException.invalidTimeWindow();
        }
    }

    // === Channel 领域逻辑已迁移至 {@link ChannelDomain}（G1 修复 Y5：
    //     Channel 子聚合的创建/状态机/短码生成是 Channel 自身关注点，不应寄生在 CampaignAggregate。
    //     validateAndResolveCode / transitionChannel / newChannel / generateBase62Code / CODE_PATTERN 见 ChannelDomain）===
}
