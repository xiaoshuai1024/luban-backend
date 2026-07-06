package com.luban.backend.shared.mapper;

import com.luban.backend.shared.entity.DomainOutbox;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

/**
 * domain_outbox Mapper（at-least-once 事件投递）。
 *
 * <p>写：Service 同事务内 insert。
 * 读/更新：OutboxRelayScheduler 扫描 fetchPending → markProcessed / incrementAttempts。
 */
@Mapper
public interface DomainOutboxMapper {

    @Insert("INSERT INTO domain_outbox (id, aggregate_id, event_type, payload_json, occurred_at, " +
            "processed_at, attempts, next_retry_at) VALUES " +
            "(#{id}, #{aggregateId}, #{eventType}, #{payloadJson}, #{occurredAt}, " +
            "#{processedAt}, #{attempts}, #{nextRetryAt})")
    @Options(useGeneratedKeys = false)
    void insert(DomainOutbox row);

    /** 扫描待处理事件（processed_at IS NULL AND next_retry_at <= now），按 occurred_at 升序，限量。 */
    @Select("SELECT * FROM domain_outbox WHERE processed_at IS NULL AND next_retry_at <= #{now} " +
            "ORDER BY occurred_at LIMIT #{limit}")
    List<DomainOutbox> fetchPending(Instant now, int limit);

    @Update("UPDATE domain_outbox SET processed_at = #{at} WHERE id = #{id}")
    void markProcessed(String id, Instant at);

    /** 失败：attempts+1，更新 next_retry_at（调用方算退避）。超过 maxAttempts 由调用方判死信。 */
    @Update("UPDATE domain_outbox SET attempts = attempts + 1, next_retry_at = #{nextRetryAt} WHERE id = #{id}")
    void incrementAttempts(String id, Instant nextRetryAt);
}
