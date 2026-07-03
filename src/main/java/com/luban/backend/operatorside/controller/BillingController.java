package com.luban.backend.operatorside.controller;

import com.luban.backend.shared.dto.SubscribeRequest;
import com.luban.backend.shared.dto.SubscriptionResponse;
import com.luban.backend.shared.dto.UsageResponse;
import com.luban.backend.operatorside.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Billing 控制器（v02 billing 域，T-be-2）。
 *
 * 路由契约（方案 §9.2）：
 * <ul>
 *   <li>GET  /billing/plans    — 套餐列表（用户鉴权）</li>
 *   <li>GET  /billing/me       — 当前订阅 + 用量聚合</li>
 *   <li>POST /billing/subscribe — 切换套餐（价格全 0，无扣费）</li>
 *   <li>GET  /billing/usage    — 当月用量</li>
 * </ul>
 * 鉴权由 BFF 注入 X-User-ID/X-User-Role。
 */
@RestController
@RequestMapping("/billing")
public class BillingController {

    private final SubscriptionService subscriptionService;

    public BillingController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /** 套餐列表。 */
    @GetMapping("/plans")
    public List<Object> plans() {
        return List.copyOf(subscriptionService.listPlans());
    }

    /** 当前用户订阅 + 套餐名 + gates + 用量。 */
    @GetMapping("/me")
    public SubscriptionService.MyPlanInfo me(
            @RequestHeader(value = "X-User-ID", required = false) String userId) {
        return subscriptionService.getMyPlan(userId);
    }

    /** 切换套餐（价格全 0）。 */
    @PostMapping("/subscribe")
    public SubscriptionResponse subscribe(
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            @Valid @RequestBody SubscribeRequest req) {
        return subscriptionService.subscribe(userId, req);
    }

    /** 当月用量。?period=YYYY-MM 可指定历史月份。 */
    @GetMapping("/usage")
    public UsageResponse usage(
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            @RequestParam(required = false) String period) {
        return subscriptionService.getUsage(userId, period);
    }
}
