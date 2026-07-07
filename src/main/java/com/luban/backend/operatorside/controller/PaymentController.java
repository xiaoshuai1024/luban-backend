package com.luban.backend.operatorside.controller;

import com.luban.backend.shared.auth.UserContext;
import com.luban.backend.shared.dto.PaymentOrderResponse;
import com.luban.backend.shared.entity.PaymentOrder;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.operatorside.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * 支付控制器（P-001 计费/订阅闭环）。
 *
 * <p>4 个端点：创建订单、查询订单、微信回调、支付宝回调。
 * 鉴权：管理端端点（createOrder/getOrder）经 X-User-ID；回调端点无鉴权（支付平台直接调用）。
 */
@RestController
@RequestMapping("/payment")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** 创建支付订单（amount==0 直通 PAID）。 */
    @PostMapping("/orders")
    public PaymentOrderResponse createOrder(@RequestBody CreateOrderRequest req) {
        String userId = UserContext.getUserId();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(401, "UNAUTHENTICATED", "未登录");
        }
        if (req.planCode() == null || req.planCode().isBlank()) {
            throw BusinessException.invalidArgument("planCode is required");
        }
        if (req.channel() == null || (!"WECHAT".equals(req.channel()) && !"ALIPAY".equals(req.channel()))) {
            throw BusinessException.invalidArgument("channel must be WECHAT or ALIPAY");
        }
        PaymentOrder order = paymentService.createOrder(userId, req.planCode(), req.channel());
        return PaymentOrderResponse.fromEntity(order);
    }

    /** 查询订单状态。 */
    @GetMapping("/orders/{id}")
    public PaymentOrderResponse getOrder(@PathVariable String id) {
        return PaymentOrderResponse.fromEntity(paymentService.getOrder(id));
    }

    /** 微信支付回调（Mock：恒成功）。 */
    @PostMapping("/callbacks/wechat")
    public String wechatCallback(@RequestBody String rawBody,
                                 @RequestHeader(value = "X-Wechat-Signature", required = false) String signature) {
        boolean ok = paymentService.handleCallback(rawBody, signature);
        if (!ok) {
            throw new BusinessException(400, "INVALID_SIGNATURE", "回调验签失败");
        }
        return "SUCCESS";
    }

    /** 支付宝回调（Mock：恒成功）。 */
    @PostMapping("/callbacks/alipay")
    public String alipayCallback(@RequestBody String rawBody,
                                 @RequestHeader(value = "X-Alipay-Signature", required = false) String signature) {
        boolean ok = paymentService.handleCallback(rawBody, signature);
        if (!ok) {
            throw new BusinessException(400, "INVALID_SIGNATURE", "回调验签失败");
        }
        return "success";
    }

    /** 创建订单请求体。 */
    public record CreateOrderRequest(String planCode, String channel) {}
}
