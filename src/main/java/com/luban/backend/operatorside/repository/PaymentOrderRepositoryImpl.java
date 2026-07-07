package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.entity.PaymentOrder;
import com.luban.backend.shared.mapper.PaymentOrderMapper;
import com.luban.backend.shared.repository.PaymentOrderRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * PaymentOrder 仓储实现：封装 {@link PaymentOrderMapper}。
 */
@Repository
public class PaymentOrderRepositoryImpl implements PaymentOrderRepository {

    private final PaymentOrderMapper paymentOrderMapper;

    public PaymentOrderRepositoryImpl(PaymentOrderMapper paymentOrderMapper) {
        this.paymentOrderMapper = paymentOrderMapper;
    }

    @Override
    public void insert(PaymentOrder order) {
        paymentOrderMapper.insert(order);
    }

    @Override
    public Optional<PaymentOrder> findById(String id) {
        return Optional.ofNullable(paymentOrderMapper.getById(id));
    }

    @Override
    public int updateStatus(String id, String status, Instant paidAt, String rawResponse) {
        return paymentOrderMapper.updateStatus(id, status, paidAt, rawResponse);
    }
}
