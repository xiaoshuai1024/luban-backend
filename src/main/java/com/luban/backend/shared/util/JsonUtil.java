package com.luban.backend.shared.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON 序列化工具（backend-ddd-refactor plan v2 G1 修复 N4）。
 *
 * <p>消除 SiteService/PageService/FormService/CollectionService/ChannelService/DatasourceService/
 * LeadService 等 7 个 Service 各自 {@code new ObjectMapper()} + 重复的 {@code jsonToString}/{@code toJson}
 * 私有方法（DRY）。所有 Service 直接调用静态方法，复用同一个线程安全的 {@link ObjectMapper}。
 *
 * <p>静态工具而非 @Component：纯无状态序列化，无依赖注入需求，调用简洁（{@code JsonUtil.toString(node)}）。
 *
 * <p>{@link ObjectMapper} 是线程安全的（官方文档），可作单例共享。
 */
public final class JsonUtil {

    /** 共享 ObjectMapper 单例（线程安全）。注册 JavaTimeModule 支持 Instant 序列化（outbox 事件持久化）。 */
    public static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private JsonUtil() {}   // 工具类禁止实例化

    /**
     * JsonNode → String；null 返回 null（保留旧值语义）。
     * 序列化失败返回 null（旧各 Service 私有方法的行为一致：宽松降级）。
     */
    public static String toString(JsonNode node) {
        if (node == null) return null;
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 任意对象 → JSON String；序列化失败返回 null（宽松降级，对齐旧 LeadService.toJson 行为）。
     */
    public static String writeValueAsString(Object obj) {
        if (obj == null) return null;
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
