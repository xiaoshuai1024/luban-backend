package com.luban.backend.shared.dto;

/**
 * Result of POST /datasources/:id/test. Field shape is identical to
 * luban-backend-go service.TestConnectionResult so both backends return the same JSON
 * (ok/message/latencyMs) — the editor shows connection health uniformly.
 */
public record DatasourceTestResult(
    boolean ok,
    String message,
    long latencyMs
) {}
