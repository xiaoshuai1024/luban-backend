package com.luban.backend.shared.domain;

import com.luban.backend.shared.entity.User;
import com.luban.backend.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UserAggregate 单测（backend-ddd-refactor plan v2 T15）。
 *
 * <p>锁定 UserAggregate 真聚合根范式的不变量：
 * <ul>
 *   <li>工厂 newUser：创建初始 active 用户，password 须为已哈希非空串</li>
 *   <li>工厂 reconstitute：从持久化重建（保留原始 status/password）</li>
 *   <li>changePassword：接收已哈希新密码，非空校验，更新 updatedAt</li>
 *   <li>disable/enable：status 状态机白名单（active/disabled）</li>
 *   <li>toEntity/pullEvents：持久化导出 + 事件拉取</li>
 * </ul>
 *
 * <p>聚合根不持有 PasswordEncoder（infra）：BCrypt 编码由 Application Service 完成，
 * 聚合根只校验"已哈希密码非空"，保持 domain 零框架依赖（工程原则）。
 */
class UserAggregateTest {

    @Test
    void newUserCreatesActiveUserWithHashedPassword() {
        Instant before = Instant.now();
        UserAggregate agg = UserAggregate.newUser(
                "u-1", "alice", "Alice", "admin", "hashed-secret");

        User entity = agg.toEntity();
        assertThat(entity.getId()).isEqualTo("u-1");
        assertThat(entity.getUsername()).isEqualTo("alice");
        assertThat(entity.getName()).isEqualTo("Alice");
        assertThat(entity.getRole()).isEqualTo("admin");
        assertThat(entity.getStatus()).isEqualTo("active");
        assertThat(entity.getPassword()).isEqualTo("hashed-secret");
        assertThat(entity.getCreatedAt()).isBetween(before, Instant.now().plusSeconds(1));
        assertThat(entity.getUpdatedAt()).isEqualTo(entity.getCreatedAt());
    }

    @Test
    void newUserDefaultsRoleToUserWhenBlank() {
        UserAggregate agg = UserAggregate.newUser(
                "u-2", "bob", "Bob", "", "hashed");
        assertThat(agg.toEntity().getRole()).isEqualTo("user");
        assertThat(agg.toEntity().getRole()).isEqualTo("user");
    }

    @Test
    void newUserRejectsBlankPassword() {
        assertThatThrownBy(() -> UserAggregate.newUser(
                "u-3", "carol", "Carol", "user", "  "))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void newUserRejectsBlankUsername() {
        assertThatThrownBy(() -> UserAggregate.newUser(
                "u-4", "", "Dan", "user", "hashed"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void reconstitutePreservesPersistedState() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        User persisted = new User();
        persisted.setId("u-9");
        persisted.setUsername("eve");
        persisted.setName("Eve");
        persisted.setRole("editor");
        persisted.setStatus("disabled");
        persisted.setPassword("old-hash");
        persisted.setCreatedAt(created);
        persisted.setUpdatedAt(created);

        UserAggregate agg = UserAggregate.reconstitute(persisted);

        User entity = agg.toEntity();
        assertThat(entity.getId()).isEqualTo("u-9");
        assertThat(entity.getStatus()).isEqualTo("disabled");
        assertThat(entity.getPassword()).isEqualTo("old-hash");
        assertThat(entity.getCreatedAt()).isEqualTo(created);
        assertThat(entity.getUpdatedAt()).isEqualTo(created);
        assertThat(agg.pullEvents()).isEmpty();   // 重建无事件
    }

    @Test
    void changePasswordUpdatesHashAndTimestamp() {
        Instant before = Instant.now();
        UserAggregate agg = UserAggregate.newUser(
                "u-1", "alice", "Alice", "admin", "old-hash");
        Instant created = agg.toEntity().getUpdatedAt();

        agg.changePassword("new-hash");

        User entity = agg.toEntity();
        assertThat(entity.getPassword()).isEqualTo("new-hash");
        assertThat(entity.getUpdatedAt()).isAfterOrEqualTo(created);
    }

    @Test
    void changePasswordRejectsBlank() {
        UserAggregate agg = UserAggregate.newUser(
                "u-1", "alice", "Alice", "admin", "old-hash");

        assertThatThrownBy(() -> agg.changePassword(null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void disableTransitionsActiveToDisabled() {
        UserAggregate agg = UserAggregate.newUser(
                "u-1", "alice", "Alice", "admin", "hash");
        assertThat(agg.toEntity().getStatus()).isEqualTo("active");

        agg.disable();

        assertThat(agg.toEntity().getStatus()).isEqualTo("disabled");
    }

    @Test
    void enableTransitionsDisabledToActive() {
        User disabled = new User();
        disabled.setId("u-2");
        disabled.setUsername("bob");
        disabled.setStatus("disabled");
        disabled.setPassword("hash");
        UserAggregate agg = UserAggregate.reconstitute(disabled);

        agg.enable();

        assertThat(agg.toEntity().getStatus()).isEqualTo("active");
    }

    @Test
    void disableOnAlreadyDisabledIsNoop() {
        User disabled = new User();
        disabled.setId("u-3");
        disabled.setUsername("carol");
        disabled.setStatus("disabled");
        disabled.setPassword("hash");
        UserAggregate agg = UserAggregate.reconstitute(disabled);

        agg.disable();   // 幂等，不抛异常

        assertThat(agg.toEntity().getStatus()).isEqualTo("disabled");
    }

    @Test
    void updateProfileAppliesNonNullFields() {
        UserAggregate agg = UserAggregate.newUser(
                "u-1", "alice", "Alice", "admin", "hash");

        agg.updateProfile("alice-new", "Alice Q", "editor");

        User entity = agg.toEntity();
        assertThat(entity.getUsername()).isEqualTo("alice-new");
        assertThat(entity.getName()).isEqualTo("Alice Q");
        assertThat(entity.getRole()).isEqualTo("editor");
    }

    @Test
    void updateProfileSkipsNullFields() {
        UserAggregate agg = UserAggregate.newUser(
                "u-1", "alice", "Alice", "admin", "hash");

        agg.updateProfile(null, "New Name", null);   // 只改 name

        User entity = agg.toEntity();
        assertThat(entity.getUsername()).isEqualTo("alice");   // 不变
        assertThat(entity.getName()).isEqualTo("New Name");
        assertThat(entity.getRole()).isEqualTo("admin");       // 不变
    }

    @Test
    void pullEventsDrainsAndIsEmptyForUserDomain() {
        // User 域当前无领域事件需求（plan §3.2 的 8 个事件不含 User）。
        // 聚合根仍须提供 pullEvents 接口保证 11 个聚合根范式统一；
        // drain 语义：连续拉取均返回空列表，且不抛异常。
        UserAggregate agg = UserAggregate.newUser(
                "u-1", "alice", "Alice", "admin", "hash");
        agg.disable();

        assertThat(agg.pullEvents()).isEmpty();
        assertThat(agg.pullEvents()).isEmpty();
    }
}
