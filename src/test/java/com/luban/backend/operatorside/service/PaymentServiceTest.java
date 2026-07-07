package com.luban.backend.operatorside.service;

import com.luban.backend.shared.entity.PaymentOrder;
import com.luban.backend.shared.entity.Plan;
import com.luban.backend.shared.port.PaymentGateway;
import com.luban.backend.shared.repository.PaymentOrderRepository;
import com.luban.backend.shared.repository.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PaymentService 单测（P-001 计费/订阅闭环 TDD）。
 *
 * <p>覆盖关键行为：
 * <ul>
 *   <li>amount==0 直通：createOrder 直接 PAID + subscribeViaPayment</li>
 *   <li>amount>0：调 PaymentGateway.createOrder，返回 PENDING</li>
 *   <li>非法 planCode → 异常</li>
 *   <li>回调幂等：已 PAID 订单跳过</li>
 *   <li>回调成功：标记 PAID + subscribeViaPayment</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentOrderRepository orderRepository;
    @Mock private PaymentGateway paymentGateway;
    @Mock private PlanRepository planRepository;
    @Mock private SubscriptionService subscriptionService;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(orderRepository, paymentGateway, planRepository, subscriptionService);
    }

    private Plan plan(String code, long price) {
        Plan p = new Plan();
        p.setPlanCode(code);
        p.setPriceMonthly(price);
        p.setName(code.toUpperCase());
        return p;
    }

    @Test
    void createOrder_amountZero_directlyPaidAndSubscribes() {
        when(planRepository.getByCode("free")).thenReturn(Optional.of(plan("free", 0)));

        PaymentOrder order = service.createOrder("user-1", "free", "WECHAT");

        assertThat(order.getStatus()).isEqualTo("PAID");
        assertThat(order.getAmount()).isEqualTo(0);
        assertThat(order.getPaidAt()).isNotNull();
        // 直通模式不调网关
        verify(paymentGateway, never()).createOrder(anyString(), org.mockito.ArgumentMatchers.anyLong(),
                anyString(), anyString());
        // 直接升级订阅
        verify(subscriptionService).subscribeViaPayment("user-1", order.getId(), "free");
    }

    @Test
    void createOrder_amountPositive_callsGatewayAndReturnsPending() {
        when(planRepository.getByCode("starter")).thenReturn(Optional.of(plan("starter", 9900)));
        when(paymentGateway.createOrder(anyString(), org.mockito.ArgumentMatchers.eq(9900L), anyString(), eq("WECHAT")))
                .thenReturn("https://pay.example.com/wx/xxx");

        PaymentOrder order = service.createOrder("user-1", "starter", "WECHAT");

        assertThat(order.getStatus()).isEqualTo("PENDING");
        assertThat(order.getPayUrl()).isEqualTo("https://pay.example.com/wx/xxx");
        // 不升级订阅（等待回调）
        verify(subscriptionService, never()).subscribeViaPayment(anyString(), anyString(), anyString());
    }

    @Test
    void createOrder_invalidPlan_throws() {
        when(planRepository.getByCode("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createOrder("user-1", "ghost", "WECHAT"))
                .isInstanceOf(com.luban.backend.shared.exception.BusinessException.class);
    }

    @Test
    void handleCallback_alreadyPaid_isIdempotent() {
        PaymentOrder paid = new PaymentOrder();
        paid.setId("order-1");
        paid.setStatus("PAID");
        paid.setUserId("user-1");
        paid.setPlanCode("starter");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(paid));
        when(paymentGateway.handleCallback(anyString(), any()))
                .thenReturn(new PaymentGateway.CallbackResult(true, "order-1", "{}"));

        boolean result = service.handleCallback("{\"orderId\":\"order-1\"}", "sig");

        assertThat(result).isTrue();
        // 幂等：不重复升级
        verify(subscriptionService, never()).subscribeViaPayment(anyString(), anyString(), anyString());
    }

    @Test
    void handleCallback_success_marksPaidAndSubscribes() {
        PaymentOrder pending = new PaymentOrder();
        pending.setId("order-2");
        pending.setStatus("PENDING");
        pending.setUserId("user-2");
        pending.setPlanCode("growth");
        when(orderRepository.findById("order-2")).thenReturn(Optional.of(pending));
        when(orderRepository.updateStatus(eq("order-2"), eq("PAID"), any(), anyString())).thenReturn(1);
        when(paymentGateway.handleCallback(anyString(), any()))
                .thenReturn(new PaymentGateway.CallbackResult(true, "order-2", "{\"paid\":true}"));

        boolean result = service.handleCallback("{\"orderId\":\"order-2\"}", "sig");

        assertThat(result).isTrue();
        verify(subscriptionService).subscribeViaPayment("user-2", "order-2", "growth");
    }

    @Test
    void handleCallback_signatureFailed_returnsFalse() {
        when(paymentGateway.handleCallback(anyString(), any()))
                .thenReturn(new PaymentGateway.CallbackResult(false, null, null));

        boolean result = service.handleCallback("bad-body", "bad-sig");

        assertThat(result).isFalse();
        verify(subscriptionService, never()).subscribeViaPayment(anyString(), anyString(), anyString());
    }
}
