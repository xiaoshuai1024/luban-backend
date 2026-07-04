package com.luban.backend.shared.repository;

import com.luban.backend.shared.domain.UserAggregate;
import com.luban.backend.shared.entity.User;

import java.util.List;

/**
 * 用户仓储接口（backend-ddd-refactor plan v2 T15）。
 *
 * <p>领域抽象（domain 层依赖此接口，不感知 MyBatis）。封装 {@code UserMapper} 的持久化细节：
 * <ul>
 *   <li>聚合根加载/保存：{@link #findById} / {@link #findByUsername} / {@link #save}</li>
 *   <li>部分字段更新（性能优化，避免全字段写）：{@link #updatePassword} / {@link #updateStatus}</li>
 *   <li>读模型查询（列表/计数，不返回聚合根）：{@link #list} / {@link #count}</li>
 * </ul>
 *
 * <p>实现见 {@code operatorside/repository/UserRepositoryImpl}（@Repository，封装 Mapper）。
 *
 * @see UserAggregate
 */
public interface UserRepository {

    /** 按 id 加载聚合根（不存在返回 null）。 */
    UserAggregate findById(String id);

    /** 按 username 加载聚合根（认证用，不存在返回 null）。 */
    UserAggregate findByUsername(String username);

    /**
     * 保存聚合根（insert or update）。
     * 判定依据：createdAt 是否已存在（新建时工厂会设 createdAt）。
     */
    void save(UserAggregate aggregate);

    /** 仅更新密码字段（避免全字段写）。由 Service 调用，聚合根 changePassword 后持久化。 */
    void updatePassword(UserAggregate aggregate);

    /** 仅更新状态字段（避免全字段写）。由 Service 调用，聚合根 disable/enable 后持久化。 */
    void updateStatus(UserAggregate aggregate);

    /**
     * 列表查询（读模型，返回 entity 而非聚合根——列表场景不需聚合根封装）。
     * @param keyword 关键字（username/name 模糊匹配，null/empty 不过滤）
     * @param offset  偏移量
     * @param size    页大小
     */
    List<User> list(String keyword, int offset, int size);

    /** 计数（读模型）。 */
    long count(String keyword);
}
