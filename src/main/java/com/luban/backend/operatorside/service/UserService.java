package com.luban.backend.operatorside.service;

import com.luban.backend.shared.domain.UserAggregate;
import com.luban.backend.shared.dto.UserListResponse;
import com.luban.backend.shared.dto.UserResponse;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户应用服务（backend-ddd-refactor plan v2 T15）。
 *
 * <p>用例编排：加载聚合根 → 调聚合根方法 → 保存 → 发布事件。
 * 业务不变量（status 白名单、password 校验、role 默认值）已下沉到 {@link UserAggregate}，
 * 本服务不含 if-else 状态判断/校验逻辑（对齐工程原则：禁贫血模型+胖 Service）。
 *
 * <p>持久化经 {@link UserRepository}（不直接依赖 Mapper，ArchUnit 守护）。
 * BCrypt 编码由本服务调 {@link PasswordEncoder} 完成，哈希传入聚合根（聚合根不持有 infra）。
 *
 * <p>UNIQUE 冲突翻译保留原行为：捕获 {@link DataIntegrityViolationException}，
 * 嗅探 message 判定是否 username 唯一冲突 → {@link BusinessException#usernameConflict()}。
 */
@Service
public class UserService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserListResponse list(int page, int size, String keyword) {
        if (page <= 0) page = 1;
        if (size <= 0) size = 10;
        int offset = (page - 1) * size;
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        var list = userRepository.list(kw, offset, size).stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());
        long total = userRepository.count(kw);
        return new UserListResponse(list, total);
    }

    public UserResponse get(String id) {
        UserAggregate agg = userRepository.findById(id);
        if (agg == null) { log.warn("用户不存在 userId（疑似越权或脏数据）"); throw BusinessException.userNotFound(); }
        return UserResponse.fromEntity(agg.toEntity());
    }

    public UserResponse create(String username, String password, String name, String role) {
        if (password == null || password.isBlank()) throw BusinessException.invalidArgument("password required");
        String hash = passwordEncoder.encode(password);
        UserAggregate agg = UserAggregate.newUser(
                UUID.randomUUID().toString(), username, name, role, hash);
        try {
            userRepository.save(agg);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e)) {
                throw BusinessException.usernameConflict();
            }
            throw e;
        }
        return UserResponse.fromEntity(agg.toEntity());
    }

    public UserResponse update(String id, String username, String name, String role, String status, String password) {
        UserAggregate agg = userRepository.findById(id);
        if (agg == null) { log.warn("用户不存在 userId（疑似越权或脏数据）"); throw BusinessException.userNotFound(); }
        agg.updateProfile(username, name, role);
        if (status != null) {
            applyStatus(agg, status);
        }
        try {
            userRepository.save(agg);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e)) {
                throw BusinessException.usernameConflict();
            }
            throw e;
        }
        if (password != null && !password.isBlank()) {
            agg.changePassword(passwordEncoder.encode(password));
            userRepository.updatePassword(agg);
        }
        return UserResponse.fromEntity(agg.toEntity());
    }

    public UserResponse updateStatus(String id, String status) {
        UserAggregate agg = userRepository.findById(id);
        if (agg == null) { log.warn("用户不存在 userId（疑似越权或脏数据）"); throw BusinessException.userNotFound(); }
        applyStatus(agg, status);
        userRepository.updateStatus(agg);
        return UserResponse.fromEntity(agg.toEntity());
    }

    /** 把字符串 status 映射到聚合根状态机方法（聚合根负责白名单与幂等）。 */
    private void applyStatus(UserAggregate agg, String status) {
        if ("disabled".equalsIgnoreCase(status)) {
            agg.disable();
        } else if ("active".equalsIgnoreCase(status)) {
            agg.enable();
        } else {
            // G1 修复 N5：未知状态值 WARN 记录（向后兼容不抛异常，但留审计痕迹便于排查脏数据）
            log.warn("未知用户状态值被忽略 userId={} status={}", agg.toEntity().getId(), status);
        }
    }

    /**
     * Detect a UNIQUE-constraint violation across DB drivers:
     *   - MySQL: "Duplicate entry ..."
     *   - H2 (MySQL mode, used in tests): "Unique index or primary key violation"
     * Real-MySQL behavior is unchanged vs. the prior "Duplicate"-only check.
     */
    private static boolean isUniqueViolation(DataIntegrityViolationException e) {
        if (e == null || e.getMessage() == null) return false;
        String m = e.getMessage();
        return m.contains("Duplicate") || m.contains("Unique index") || m.contains("primary key violation");
    }
}
