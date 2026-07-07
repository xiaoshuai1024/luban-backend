package com.luban.backend.operatorside.service;

import com.luban.backend.shared.port.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mock 支付网关实现（P-001 计费/订阅闭环，内测期）。
 *
 * <p>不调真实微信/支付宝 API。createOrder 返回占位支付 URL；
 * handleCallback 恒返回成功（模拟支付平台回调）。
 *
 * <p>当 {@code payment.mode=mock}（默认）时启用。未来接真实支付时：
 * 新增 {@code WechatPaymentGateway}（{@code @ConditionalOnProperty(name="payment.mode", havingValue="wechat")}）
 * 并将本类改为 {@code havingValue="mock"}。
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    @Value("${payment.mock.base-url:https://luban.example.com/mock-pay}")
    private String mockBaseUrl;

    @Override
    public String createOrder(String orderId, long amount, String description, String channel) {
        String payUrl = mockBaseUrl + "/" + orderId;
        log.info("MockPaymentGateway createOrder: orderId={}, amount={}, channel={}, payUrl={}",
                orderId, amount, channel, payUrl);
        return payUrl;
    }

    @Override
    public CallbackResult handleCallback(String rawBody, String signature) {
        // Mock：不验签，恒返回成功。从 rawBody 提取 orderId（Mock 格式 {"orderId":"xxx"}）。
        String orderId = extractMockOrderId(rawBody);
        log.info("MockPaymentGateway handleCallback: orderId={}, rawBody={}", orderId, rawBody);
        return new CallbackResult(true, orderId, rawBody);
    }

    /** 从 Mock 回调体提取 orderId（简单 JSON 解析，避免引入 Jackson 依赖）。 */
    private String extractMockOrderId(String rawBody) {
        if (rawBody == null) return null;
        // Mock 格式：{"orderId":"xxx"} 或裸 orderId
        int idx = rawBody.indexOf("\"orderId\"");
        if (idx < 0) return rawBody.replaceAll("[^a-zA-Z0-9-]", "");
        int colon = rawBody.indexOf(':', idx);
        if (colon < 0) return null;
        int start = rawBody.indexOf('"', colon + 1);
        int end = rawBody.indexOf('"', start + 1);
        if (start < 0 || end < 0) return null;
        return rawBody.substring(start + 1, end);
    }
}
