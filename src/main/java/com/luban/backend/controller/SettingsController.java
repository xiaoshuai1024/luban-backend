package com.luban.backend.controller;

import com.luban.backend.service.SettingsService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> get() {
        String json = settingsService.get();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json.getBytes(StandardCharsets.UTF_8));
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> update(@RequestBody byte[] body) {
        String json = body != null ? new String(body, StandardCharsets.UTF_8) : "{}";
        String updated = settingsService.update(json);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(updated.getBytes(StandardCharsets.UTF_8));
    }
}
