package com.luban.backend.mapper;

import com.luban.backend.entity.AnalyticsEvent;
import org.apache.ibatis.annotations.*;

import java.time.Instant;
import java.util.List;

/**
 * AnalyticsEvent mapper（v02 analytics 域）。
 */
@Mapper
public interface AnalyticsEventMapper {

    String COLS = "id, site_id, visitor_id, session_id, event_type, event_payload, page_id, variant_id, utm_json, client_ts, server_ts, source_ip_hashed";

    @Insert("INSERT INTO analytics_events (" + COLS + ") VALUES (" +
            "#{id}, #{siteId}, #{visitorId}, #{sessionId}, #{eventType}, #{eventPayload}, " +
            "#{pageId}, #{variantId}, #{utmJson}, #{clientTs}, #{serverTs}, #{sourceIpHashed})")
    int insert(AnalyticsEvent event);

    /** 查某站点时间范围内的事件（聚合/查询用）。 */
    @Select("SELECT " + COLS + " FROM analytics_events " +
            "WHERE site_id = #{siteId} AND server_ts >= #{from} AND server_ts < #{to} " +
            "ORDER BY server_ts ASC")
    List<AnalyticsEvent> listBySiteAndTimeRange(@Param("siteId") String siteId,
                                                 @Param("from") Instant from,
                                                 @Param("to") Instant to);

    /** 查某站点某天的事件（预聚合用）。 */
    @Select("SELECT " + COLS + " FROM analytics_events " +
            "WHERE site_id = #{siteId} AND DATE(server_ts) = #{date}")
    List<AnalyticsEvent> listBySiteAndDate(@Param("siteId") String siteId, @Param("date") String date);
}
