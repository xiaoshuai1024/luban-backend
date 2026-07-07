package com.luban.backend.operatorside.service;

import com.luban.backend.shared.entity.PaymentOrder;
import com.luban.backend.shared.entity.Plan;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.port.PaymentGateway;
import com.luban.backend.shared.repository.PaymentOrderRepository;
import com.luban.backend.shared.repository.PlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * 支付编排服务（P-001 计费/订阅闭环）。
 *
 * <p>编排支付订单的创建、金额校验、amount==0 直通、回调处理、订阅升级。
 *
 * <p>关键业务规则：
 * <ul>
 *   <li>amount==0 直通：plan.priceMonthly==0 时，不调真实支付，直接 PAID + subscribeViaPayment</li>
 *   <li>幂等：回调处理时检查订单是否已 PAID，已 PAID 则跳过</li>
 * </ul>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentOrderRepository orderRepository;
    private final PaymentGateway paymentGateway;
    private final PlanRepository planRepository;
    private final SubscriptionService subscriptionService;

    public PaymentService(PaymentOrderRepository orderRepository,
                          PaymentGateway paymentGateway,
                          PlanRepository planRepository,
                          SubscriptionService subscriptionService) {
        this.orderRepository = orderRepository;
        this.paymentGateway = paymentGateway;
        this.planRepository = planRepository;
        this.subscriptionService = subscriptionService;
    }

    /**
     * 创建支付订单。
     *
     * <p>若 plan.priceMonthly==0（内测期免费），直接标记 PAID 并升级订阅（直通）。
     * 否则调 PaymentGateway.createOrder 获取支付链接，返回 PENDING 订单。
     *
     * @return 已创建的订单（PAID 或 PENDING）
     */
    @Transactional(rollbackFor = Exception.class)
    public PaymentOrder createOrder(String userId, String planCode, String channel) {
        // 1. 校验 plan 存在
        Plan plan = planRepository.getByCode(planCode)
                .orElseThrow(() -> BusinessException.invalidArgument("未知套餐: " + planCode));

        // 2. 创建订单记录
        String orderId = UUID.randomUUID().toString();
        long amount = plan.getPriceMonthly();
        Instant now = Instant.now();

        PaymentOrder order = new PaymentOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setPlanCode(planCode);
        order.setAmount(amount);
        order.setCurrency("CNY");
        order.setChannel(channel);
        order.setCreatedAt(now);

        if (amount == 0) {
            // 3a. 内测期直通：amount==0 → 直接 PAID + 升级订阅
            order.setStatus("PAID");
            order.setPaidAt(now);
            order.setPayUrl(null);
            order.setRawResponse("{\"mode\":\"mock\",\"reason\":\"amount==0 直通\"}");
            orderRepository.insert(order);
            subscriptionService.subscribeViaPayment(userId, orderId, planCode);
            log.info("支付订单直通 PAID（amount==0）：orderId={}, userId={}, planCode={}", orderId, userId, planCode);
        } else {
            // 3b. 真实支付：调网关获取支付链接
            String payUrl = paymentGateway.createOrder(orderId, amount,
                    "Luban " + plan.getName() + " 套餐", channel);
            order.setStatus("PENDING");
            order.setPayUrl(payUrl);
            order.setPaidAt(null);
            orderRepository.insert(order);
            log.info("支付订单创建 PENDING：orderId={}, userId={}, planCode={}, amount={}, payUrl={}",
                    orderId, userId, planCode, amount, payUrl);
        }

        return order;
    }

    /**
     * 查询订单状态。
     */
    public PaymentOrder getOrder(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(404, "ORDER_NOT_FOUND", "订单不存在"));
    }

    /**
     * 处理支付回调（微信/支付宝）。
     *
     * <p>幂等：订单已 PAID 则跳过。
     * 验签 + 解析 → 标记 PAID → 升级订阅。
     *
     * @return true 表示处理成功（或幂等跳过），false 表示验签失败
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean handleCallback(String rawBody, String signature) {
        PaymentGateway.CallbackResult result = paymentGateway.handleCallback(rawBody, signature);
        if (!result.success()) {
            log.warn("支付回调验签失败");
            return false;
        }

        String orderId = result.orderId();
        PaymentOrder order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("支付回调订单不存在：orderId={}", orderId);
            return false;
        }

        // 幂等：已 PAID 则跳过
        if ("PAID".equals(order.getStatus())) {
            log.info("支付回调幂等跳过（已 PAID）：orderId={}", orderId);
            return true;
        }

        // 标记 PAID + 升级订阅
        int updated = orderRepository.updateStatus(orderId, "PAID", Instant.now(), result.rawResponse());
        if (updated == 0) {
            log.warn("支付回调状态更新失败（并发或状态冲突）：orderId={}", orderId);
            return false;
        }

        subscriptionService.subscribeViaPayment(order.getUserId(), orderId, order.getPlanCode());
        log.info("支付回调处理成功：orderId={}, userId={}, planCode={}", orderId, order.getUserId(), order.getPlanCode());
        return true;
    }
}
