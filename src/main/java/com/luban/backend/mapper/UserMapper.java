package com.luban.backend.mapper;

import com.luban.backend.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserMapper {

    @Select("SELECT id, username, name, role, status, password, created_at, updated_at FROM users WHERE username = #{username}")
    User findByUsername(String username);

    @Select("SELECT id, username, name, role, status, password, created_at, updated_at FROM users WHERE id = #{id}")
    User getById(String id);

    @Select("SELECT id, username, name, role, status, password, created_at, updated_at FROM users " +
            "WHERE (#{keyword} IS NULL OR #{keyword} = '' OR username LIKE CONCAT('%', #{keyword}, '%') OR name LIKE CONCAT('%', #{keyword}, '%')) " +
            "ORDER BY created_at DESC LIMIT #{size} OFFSET #{offset}")
    List<User> list(@Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(1) FROM users " +
            "WHERE (#{keyword} IS NULL OR #{keyword} = '' OR username LIKE CONCAT('%', #{keyword}, '%') OR name LIKE CONCAT('%', #{keyword}, '%'))")
    long count(@Param("keyword") String keyword);

    @Insert("INSERT INTO users (id, username, name, role, status, password, created_at, updated_at) " +
            "VALUES (#{id}, #{username}, #{name}, #{role}, #{status}, #{password}, #{createdAt}, #{updatedAt})")
    int insert(User user);

    @Update("UPDATE users SET username=#{username}, name=#{name}, role=#{role}, status=#{status}, updated_at=#{updatedAt} WHERE id=#{id}")
    int update(User user);

    @Update("UPDATE users SET password=#{password}, updated_at=#{updatedAt} WHERE id=#{id}")
    int updatePassword(@Param("id") String id, @Param("password") String password, @Param("updatedAt") java.time.Instant updatedAt);

    @Update("UPDATE users SET status=#{status}, updated_at=#{updatedAt} WHERE id=#{id}")
    int updateStatus(@Param("id") String id, @Param("status") String status, @Param("updatedAt") java.time.Instant updatedAt);
}
