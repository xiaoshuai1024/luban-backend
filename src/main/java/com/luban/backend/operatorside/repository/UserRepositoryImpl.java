package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.domain.UserAggregate;
import com.luban.backend.shared.entity.User;
import com.luban.backend.shared.mapper.UserMapper;
import com.luban.backend.shared.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户仓储实现（backend-ddd-refactor plan v2 T15）。
 *
 * <p>封装 {@link UserMapper}（MyBatis），实现 {@link UserRepository}。
 * 持久化异常翻译：{@link DataIntegrityViolationException} 的 UNIQUE 冲突
 * 由 Service 层捕获翻译为 {@code BusinessException.usernameConflict()}（保留原行为）。
 *
 * <p>save 判定 insert/update：UserMapper 无 upsert，按"createdAt 是否已设"区分新建/更新。
 * 新建（工厂 newUser 已设 createdAt）→ insert；重建（reconstitute 保留持久化 createdAt）→ update。
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;

    public UserRepositoryImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserAggregate findById(String id) {
        User user = userMapper.getById(id);
        return user != null ? UserAggregate.reconstitute(user) : null;
    }

    @Override
    public UserAggregate findByUsername(String username) {
        User user = userMapper.findByUsername(username);
        return user != null ? UserAggregate.reconstitute(user) : null;
    }

    @Override
    public void save(UserAggregate aggregate) {
        User entity = aggregate.toEntity();
        // 判定新建 vs 更新：新建时 createdAt == updatedAt（工厂同时设）；
        // 重建/更新时聚合根的 entity 已有持久化 createdAt，updatedAt 被 touch() 推进。
        // 更稳妥：查询是否存在该 id。
        if (userMapper.getById(entity.getId()) == null) {
            userMapper.insert(entity);
        } else {
            userMapper.update(entity);
        }
    }

    @Override
    public void updatePassword(UserAggregate aggregate) {
        User entity = aggregate.toEntity();
        userMapper.updatePassword(entity.getId(), entity.getPassword(), entity.getUpdatedAt());
    }

    @Override
    public void updateStatus(UserAggregate aggregate) {
        User entity = aggregate.toEntity();
        userMapper.updateStatus(entity.getId(), entity.getStatus(), entity.getUpdatedAt());
    }

    @Override
    public List<User> list(String keyword, int offset, int size) {
        return userMapper.list(keyword, offset, size);
    }

    @Override
    public long count(String keyword) {
        return userMapper.count(keyword);
    }
}
