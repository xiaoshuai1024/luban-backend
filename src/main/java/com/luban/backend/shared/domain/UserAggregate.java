package com.luban.backend.shared.domain;

import com.luban.backend.shared.domain.event.DomainEvent;
import com.luban.backend.shared.entity.User;
import com.luban.backend.shared.exception.BusinessException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户聚合根（backend-ddd-refactor plan v2 T15）。
 *
 * <p>封装 User 域不变量（对齐 {@code .agents/rules/luban-engineering-principles.md} DDD 专项约束）：
 * <ul>
 *   <li><b>status 白名单</b>：active / disabled（disable/enable 状态机，幂等）</li>
 *   <li><b>password 必须为已哈希非空串</b>：聚合根不持有 {@code PasswordEncoder}（infra），
 *       BCrypt 编码由 Application Service 完成后传入，聚合根只校验"已哈希密码非空"，
 *       保持 domain 零框架依赖</li>
 *   <li><b>role 默认 user</b>：工厂创建时空值兜底为 "user"</li>
 *   <li><b>username 非空</b></li>
 * </ul>
 *
 * <p>聚合根不感知持久化（Mapper）与调用方（Service/Controller），持久化由 Repository 负责。
 *
 * @see User
 */
public final class UserAggregate {

    /** 允许的用户状态白名单。 */
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_DISABLED = "disabled";

    private final User root;
    private final List<DomainEvent> events = new ArrayList<>();

    private UserAggregate(User root) {
        this.root = root;
    }

    /**
     * 工厂：创建新用户（初始 status=active）。
     *
     * @param id           用户 id
     * @param username     用户名（非空）
     * @param name         显示名（null 兜底空串）
     * @param role         角色（null/blank 兜底 "user"）
     * @param passwordHash 已 BCrypt 编码的密码哈希（非空，由 Service 调 PasswordEncoder 编码后传入）
     */
    public static UserAggregate newUser(String id, String username, String name, String role, String passwordHash) {
        if (username == null || username.isBlank()) {
            throw BusinessException.invalidArgument("username required");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw BusinessException.invalidArgument("password required");
        }
        Instant now = Instant.now();
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setName(name != null ? name : "");
        user.setRole(role != null && !role.isBlank() ? role : "user");
        user.setStatus(STATUS_ACTIVE);
        user.setPassword(passwordHash);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return new UserAggregate(user);
    }

    /**
     * 工厂：从持久化重建（保留原始 status/password/timestamp，不发事件）。
     */
    public static UserAggregate reconstitute(User persisted) {
        return new UserAggregate(persisted);
    }

    /** 禁用用户（active→disabled，幂等：已 disabled 不抛异常）。 */
    public void disable() {
        if (STATUS_DISABLED.equals(root.getStatus())) {
            return;   // 幂等
        }
        root.setStatus(STATUS_DISABLED);
        touch();
    }

    /** 启用用户（disabled→active）。 */
    public void enable() {
        if (STATUS_ACTIVE.equals(root.getStatus())) {
            return;
        }
        root.setStatus(STATUS_ACTIVE);
        touch();
    }

    /**
     * 修改密码。
     *
     * @param newHash 新的 BCrypt 哈希（非空，由 Service 调 PasswordEncoder 编码后传入）
     */
    public void changePassword(String newHash) {
        if (newHash == null || newHash.isBlank()) {
            throw BusinessException.invalidArgument("password required");
        }
        root.setPassword(newHash);
        touch();
    }

    /**
     * 更新资料（null 字段跳过，保留原值）。
     *
     * @param username 新用户名（null 跳过）
     * @param name     新显示名（null 跳过）
     * @param role     新角色（null 跳过）
     */
    public void updateProfile(String username, String name, String role) {
        if (username != null) {
            if (username.isBlank()) {
                throw BusinessException.invalidArgument("username required");
            }
            root.setUsername(username);
        }
        if (name != null) {
            root.setName(name);
        }
        if (role != null) {
            root.setRole(role);
        }
        touch();
    }

    /** 导出持久化实体（Repository.save 用）。调用者不应持有此引用后继续修改聚合根。 */
    public User toEntity() {
        return root;
    }

    /** 拉取并清空待发布领域事件（Repository.save 后由 Service 发布）。当前 User 域无事件需求，返回空列表。 */
    public List<DomainEvent> pullEvents() {
        List<DomainEvent> drained = new ArrayList<>(events);
        events.clear();
        return drained;
    }

    private void touch() {
        root.setUpdatedAt(Instant.now());
    }
}
