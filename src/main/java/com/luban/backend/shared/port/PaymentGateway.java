package com.luban.backend.shared.port;

/**
 * 支付网关端口接口（P-001 计费/订阅闭环，依赖倒置）。
 *
 * <p>抽象微信/支付宝支付能力。当前由 {@code MockPaymentGateway} 实现（内测期不接真实接口）。
 * 未来接真实支付时，新增 {@code WechatPaymentGateway} / {@code AlipayPaymentGateway} 实现即可。
 *
 * <p>Port 接口置于 shared，实现置于 operatorside，Service 仅依赖接口。
 */
public interface PaymentGateway {

    /**
     * 创建支付订单（向支付平台下单）。
     *
     * @param orderId  内部订单 ID
     * @param amount   金额（分）
     * @param description 商品描述
     * @param channel  支付渠道（WECHAT / ALIPAY）
     * @return 支付链接/二维码 URL（Mock 返回占位 URL）
     */
    String createOrder(String orderId, long amount, String description, String channel);

    /**
     * 处理支付平台回调（验签 + 解析订单状态）。
     *
     * @param rawBody   回调原始请求体
     * @param signature 签名（headers 中提取）
     * @return 回调解析结果
     */
    CallbackResult handleCallback(String rawBody, String signature);

    /** 回调解析结果。 */
    record CallbackResult(boolean success, String orderId, String rawResponse) {}
}
