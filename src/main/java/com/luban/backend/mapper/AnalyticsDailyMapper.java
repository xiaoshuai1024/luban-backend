package com.luban.backend.mapper;

import com.luban.backend.entity.AnalyticsDaily;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

/**
 * AnalyticsDaily mapper（v02 analytics 域）。
 * upsert 利用 UNIQUE KEY uk_ad(site_id, date, page_id, variant_id) 原子累加。
 */
@Mapper
public interface AnalyticsDailyMapper {

    String COLS = "id, site_id, date, page_id, variant_id, views, submissions, conversions";

    /** 原子 upsert：存在则累加，不存在则插入。 */
    @Insert("INSERT INTO analytics_daily (" + COLS + ") VALUES (" +
            "#{id}, #{siteId}, #{date}, #{pageId}, #{variantId}, #{views}, #{submissions}, #{conversions}) " +
            "ON DUPLICATE KEY UPDATE views = views + VALUES(views), " +
            "submissions = submissions + VALUES(submissions), conversions = conversions + VALUES(conversions)")
    int upsert(AnalyticsDaily daily);

    /** 查某站点日期范围内的预聚合数据。 */
    @Select("SELECT " + COLS + " FROM analytics_daily " +
            "WHERE site_id = #{siteId} AND date >= #{from} AND date <= #{to} " +
            "ORDER BY date ASC")
    List<AnalyticsDaily> listBySiteAndDateRange(@Param("siteId") String siteId,
                                                 @Param("from") LocalDate from,
                                                 @Param("to") LocalDate to);
}
