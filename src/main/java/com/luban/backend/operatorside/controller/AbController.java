package com.luban.backend.operatorside.controller;

import com.luban.backend.operatorside.service.AbService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AB 实验控制器（v02 ab 域，T-be-10/11）。
 *
 * 路由：
 * <ul>
 *   <li>GET /public/ab/assign?visitorId&pageId — 访客分桶（免鉴权）</li>
 *   <li>GET /ab/experiments?siteId&status? — 实验列表（管理端）</li>
 *   <li>POST /ab/experiments — 创建实验（含变体）</li>
 *   <li>GET /ab/experiments/:id — 实验详情</li>
 *   <li>POST /ab/experiments/:id/end — 结束实验</li>
 *   <li>GET /ab/experiments/:id/significance — 显著性检验</li>
 * </ul>
 */
@RestController
public class AbController {

    private final AbService abService;

    public AbController(AbService abService) {
        this.abService = abService;
    }

    // ===== 公开端点（免鉴权，访客分桶）=====

    /** GET /public/ab/assign — 访客分桶，返回 {experimentId?, variantId?, inExperiment}。 */
    @GetMapping("/public/ab/assign")
    public AbService.AssignResult assign(@RequestParam String visitorId, @RequestParam String pageId) {
        return abService.assignVariant(visitorId, pageId);
    }

    // ===== 管理端（鉴权由 BFF 注入 X-User-ID，多租户 siteId 隔离）=====

    @GetMapping("/ab/experiments")
    public List<Object> listExperiments(@RequestParam String siteId,
                                        @RequestParam(required = false) String status) {
        return List.copyOf(abService.listExperiments(siteId, status));
    }

    @GetMapping("/ab/experiments/{id}")
    public AbService.AbExperimentDetail getExperiment(@PathVariable String id) {
        return abService.getExperimentDetail(id);
    }

    @PostMapping("/ab/experiments")
    public Object createExperiment(@RequestBody AbService.CreateExperimentInput input) {
        return abService.createExperiment(input);
    }

    @PostMapping("/ab/experiments/{id}/end")
    public Object endExperiment(@PathVariable String id) {
        return abService.endExperiment(id);
    }

    @GetMapping("/ab/experiments/{id}/significance")
    public AbService.SignificanceResult significance(@PathVariable String id) {
        return abService.computeSignificance(id);
    }
}
