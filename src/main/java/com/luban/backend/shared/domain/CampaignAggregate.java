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
 * <p><b>Channel 领域逻辑</b>（创建/状态机/短码生成/类型枚举）已全部迁移至 {@link ChannelDomain}——
 * Channel 是 Campaign 的子聚合，其领域逻辑属 Channel 自身关注点，不应寄生在 Campaign 聚合根上（SRP）。
 */
public final class CampaignAggregate {

    // === 状态常量（聚合根自身状态机用，无外部引用） ===
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
        validateCreateParams(name, startAt, endAt);
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

    /**
     * 校验 Campaign 创建参数（工厂内部不变量）。endAt &gt;= startAt（若都提供）。
     * 私有——仅 {@link #newCampaign} 调用，无外部引用，不暴露为 public static。
     */
    private static void validateCreateParams(String name, Instant startAt, Instant endAt) {
        if (name == null || name.isBlank()) {
            throw BusinessException.missingField("name");
        }
        if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
            throw BusinessException.invalidTimeWindow();
        }
    }
}
