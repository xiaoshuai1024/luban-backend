package com.luban.backend.operatorside.controller;

import com.luban.backend.operatorside.service.CampaignService;
import com.luban.backend.shared.dto.CampaignResponse;
import com.luban.backend.shared.dto.CampaignSaveRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 活动管理（运营端，app-deeplink-backend-arch plan T13）。
 *
 * <p>鉴权：AuthFilter 保护（/campaigns 需 X-User-ID），与 FormController 同模式。
 */
@RestController
@RequestMapping("/campaigns")
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @GetMapping
    public List<CampaignResponse> list(@RequestParam String siteId) {
        return campaignService.list(siteId);
    }

    @GetMapping("/{id}")
    public CampaignResponse get(@RequestParam String siteId, @PathVariable String id) {
        return campaignService.get(siteId, id);
    }

    @PostMapping
    public ResponseEntity<CampaignResponse> create(@Valid @RequestBody CampaignSaveRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.create(req));
    }

    @PutMapping("/{id}")
    public CampaignResponse update(@RequestParam String siteId, @PathVariable String id,
                                   @Valid @RequestBody CampaignSaveRequest req) {
        return campaignService.update(siteId, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestParam String siteId, @PathVariable String id) {
        campaignService.delete(siteId, id);
        return ResponseEntity.noContent().build();
    }
}
