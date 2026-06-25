package com.luban.backend.service;

import com.luban.backend.entity.UsageCounter;
import com.luban.backend.mapper.UsageCounterMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 用量计数服务（v02 billing 域）。
 *
 * 原子递增：INSERT ... ON DUPLICATE KEY UPDATE count = count + 1（MySQL 原子，并发安全）。
 * 被 QuotaInterceptor 在资源创建（Lead/Page 提交、页面访问）时调用。
 */
@Service
public class UsageService {

    private final UsageCounterMapper usageCounterMapper;

    public UsageService(UsageCounterMapper usageCounterMapper) {
        this.usageCounterMapper = usageCounterMapper;
    }

    /** 递增某 metric 用量（leads/pages/visits）。period 取当月 UTC。 */
    public void increment(String userId, String metric) {
        String periodMonth = LocalDate.now(ZoneOffset.UTC).toString().substring(0, 7);
        UsageCounter counter = new UsageCounter();
        counter.setId(UUID.randomUUID().toString());
        counter.setUserId(userId);
        counter.setPeriodMonth(periodMonth);
        counter.setMetric(metric);
        usageCounterMapper.incrementOrInsert(counter);
    }

    /** 查询某用户当月某 metric 用量。 */
    public long getCount(String userId, String metric) {
        String periodMonth = LocalDate.now(ZoneOffset.UTC).toString().substring(0, 7);
        return usageCounterMapper.listByUserPeriod(userId, periodMonth).stream()
                .filter(c -> metric.equals(c.getMetric()))
                .mapToLong(UsageCounter::getCount)
                .findFirst()
                .orElse(0);
    }
}
