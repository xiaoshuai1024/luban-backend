package com.luban.backend.operatorside.controller;

import com.luban.backend.operatorside.service.ChannelService;
import com.luban.backend.shared.dto.ChannelResponse;
import com.luban.backend.shared.dto.ChannelSaveRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 渠道/短链管理（运营端，app-deeplink-backend-arch plan T12）。
 *
 * <p>鉴权：AuthFilter 保护（/channels 需 X-User-ID），与 FormController 同模式。
 * 站点级权限（TenantGuard）后续按需在 Service 层补。
 */
@RestController
@RequestMapping("/channels")
public class ChannelController {

    private final ChannelService channelService;

    public ChannelController(ChannelService channelService) {
        this.channelService = channelService;
    }

    @GetMapping
    public List<ChannelResponse> list(@RequestParam String siteId) {
        return channelService.list(siteId);
    }

    @GetMapping("/{id}")
    public ChannelResponse get(@RequestParam String siteId, @PathVariable String id) {
        return channelService.get(siteId, id);
    }

    @PostMapping
    public ResponseEntity<ChannelResponse> create(@Valid @RequestBody ChannelSaveRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(channelService.create(req));
    }

    @PutMapping("/{id}")
    public ChannelResponse update(@RequestParam String siteId, @PathVariable String id,
                                  @Valid @RequestBody ChannelSaveRequest req) {
        return channelService.update(siteId, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestParam String siteId, @PathVariable String id) {
        channelService.delete(siteId, id);
        return ResponseEntity.noContent().build();
    }
}
