package com.luban.backend.controller;

import com.luban.backend.service.FeatureGateService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 特性开关控制器（plan §9.2 / §3.5）：
 * <ul>
 *   <li>GET /feature-gates?siteId=&key= （管理端，鉴权）→ {enabled: bool}</li>
 *   <li>GET /public/feature-gates?siteId=&key= （访客侧公开，无鉴权）→ {enabled: bool}</li>
 *   <li>PUT /feature-gates?siteId=&key=&enabled= （管理端设置）</li>
 * </ul>
 * /public/* 由 AuthFilter 放行（无需 X-User-ID）。
 */
@RestController
public class FeatureGateController {

    private final FeatureGateService featureGateService;

    public FeatureGateController(FeatureGateService featureGateService) {
        this.featureGateService = featureGateService;
    }

    /** 管理端读取开关（鉴权由 BFF 注入 X-User-ID/X-User-Role）。 */
    @GetMapping("/feature-gates")
    public Map<String, Object> get(@RequestParam String siteId, @RequestParam String key) {
        return Map.of("siteId", siteId, "key", key, "enabled", featureGateService.isEnabled(siteId, key));
    }

    /** 管理端设置开关。 */
    @PutMapping("/feature-gates")
    public Map<String, Object> set(@RequestParam String siteId, @RequestParam String key,
                                   @RequestParam boolean enabled) {
        featureGateService.setEnabled(siteId, key, enabled);
        return Map.of("siteId", siteId, "key", key, "enabled", enabled);
    }

    /** 访客侧公开读取（无鉴权，仅返回布尔，避免访客被 401 拦截）。 */
    @GetMapping("/public/feature-gates")
    public Map<String, Object> getPublic(@RequestParam String siteId, @RequestParam String key) {
        return Map.of("enabled", featureGateService.isEnabled(siteId, key));
    }
}
