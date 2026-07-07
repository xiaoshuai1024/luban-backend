package com.luban.backend.shared.repository;

import com.luban.backend.shared.entity.PaymentOrder;

import java.util.Optional;

/**
 * PaymentOrder 仓储接口（domain 抽象，不依赖 MyBatis）。
 */
public interface PaymentOrderRepository {

    void insert(PaymentOrder order);

    Optional<PaymentOrder> findById(String id);

    /** 原子更新状态（含 paidAt + rawResponse）。返回受影响行数（0=订单不存在或状态冲突）。 */
    int updateStatus(String id, String status, java.time.Instant paidAt, String rawResponse);
}
