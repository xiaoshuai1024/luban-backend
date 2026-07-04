package com.luban.backend.shared.domain;

import com.luban.backend.shared.domain.event.DomainEvent;
import com.luban.backend.shared.entity.Datasource;
import com.luban.backend.shared.exception.BusinessException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 数据源聚合根（backend-ddd-refactor plan v2 T11）。
 *
 * <p>封装 Datasource 域不变量（对齐 {@code .agents/rules/luban-engineering-principles.md}）：
 * <ul>
 *   <li><b>type 白名单</b>：{@code static} / {@code api}（非法值抛 INVALID_ARGUMENT，
 *       与 Go 后端 parity 锁定，见 {@code DatasourceContractTest}）</li>
 *   <li><b>siteId 不可变</b>：创建后聚合根不暴露修改 siteId 的方法（多租户归属）</li>
 * </ul>
 *
 * <p><b>不封装的部分（属 infra，留 Service）</b>：
 * <ul>
 *   <li>HTTP testConnection：网络 IO，聚合根零 infra 依赖，留在 {@code DatasourceService.testConnection}</li>
 *   <li>configJson 序列化（JsonNode→String）：依赖 ObjectMapper，Service 完成后传入聚合根</li>
 *   <li>name UNIQUE 冲突翻译：持久化异常，由 RepositoryImpl/Service 翻译为 DATASOURCE_NAME_CONFLICT</li>
 * </ul>
 *
 * @see Datasource
 */
public final class DatasourceAggregate {

    /** 允许的数据源类型白名单（与 Go 后端 parity 锁定）。 */
    public static final Set<String> ALLOWED_TYPES = Set.of("static", "api");
    public static final String TYPE_STATIC = "static";
    public static final String TYPE_API = "api";

    private final Datasource root;
    private final List<DomainEvent> events = new ArrayList<>();

    private DatasourceAggregate(Datasource root) {
        this.root = root;
    }

    /**
     * 工厂：创建新数据源（type 须过白名单）。
     *
     * @param id         数据源 id
     * @param siteId     所属站点 id（创建后不可变）
     * @param name       名称（UNIQUE 约束在 DB 层，冲突翻译在 Service/Repository）
     * @param type       类型（须 ∈ {@link #ALLOWED_TYPES}）
     * @param configJson 已序列化的配置 JSON 字符串（Service 负责 JsonNode→String）
     */
    public static DatasourceAggregate newDatasource(String id, String siteId, String name,
                                                    String type, String configJson) {
        validateType(type);
        Instant now = Instant.now();
        Datasource ds = new Datasource();
        ds.setId(id);
        ds.setSiteId(siteId);
        ds.setName(name);
        ds.setType(type);
        ds.setConfigJson(configJson);
        ds.setCreatedAt(now);
        ds.setUpdatedAt(now);
        return new DatasourceAggregate(ds);
    }

    /**
     * 工厂：从持久化重建（保留原始字段，不发事件）。
     */
    public static DatasourceAggregate reconstitute(Datasource persisted) {
        return new DatasourceAggregate(persisted);
    }

    /**
     * 更新数据源（type 须过白名单，siteId 不变）。
     *
     * @param name       新名称
     * @param type       新类型（须 ∈ {@link #ALLOWED_TYPES}）
     * @param configJson 新配置 JSON（Service 已序列化）
     */
    public void update(String name, String type, String configJson) {
        validateType(type);   // 先校验，失败则聚合根状态不变
        root.setName(name);
        root.setType(type);
        root.setConfigJson(configJson);
        root.setUpdatedAt(Instant.now());
    }

    /** 是否 static 类型（Service.testConnection 用，static 无需探测远程）。 */
    public boolean isStaticType() {
        return TYPE_STATIC.equals(root.getType());
    }

    /** 导出持久化实体（Repository.save 用）。 */
    public Datasource toEntity() {
        return root;
    }

    /** 配置 JSON 原始字符串访问（Service.testConnection 解析 url 用）。 */
    public String getConfigJson() {
        return root.getConfigJson();
    }

    /** 拉取并清空待发布领域事件。当前 Datasource 域无事件需求，返回空列表。 */
    public List<DomainEvent> pullEvents() {
        List<DomainEvent> drained = new ArrayList<>(events);
        events.clear();
        return drained;
    }

    private static void validateType(String type) {
        if (type == null || !ALLOWED_TYPES.contains(type)) {
            throw BusinessException.invalidArgument("type must be one of " + ALLOWED_TYPES);
        }
    }
}
