package com.luban.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.dto.DatasourceResponse;
import com.luban.backend.dto.DatasourceTestResult;
import com.luban.backend.entity.Datasource;
import com.luban.backend.exception.BusinessException;
import com.luban.backend.mapper.DatasourceMapper;
import com.luban.backend.mapper.SiteMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Datasource CRUD + connection test. Aligned with luban-backend-go
 * internal/service/datasource_service.go (same {@code type} whitelist, same
 * NAME_CONFLICT/SITE_NOT_FOUND/DATASOURCE_NOT_FOUND error codes, same
 * TestConnection return shape).
 *
 * <p>{@code testConnection} only applies to type={@code api} (HTTP GET against the
 * configured {@code url}). For {@code static} datasources there is no remote to
 * probe — we return ok=true with latency 0 immediately, matching the Go backend.
 */
@Service
public class DatasourceService {

    /** Whitelist enforced identically on both backends (plan §3). */
    static final Set<String> ALLOWED_TYPES = Set.of("static", "api");

    private static final int TEST_TIMEOUT_SECONDS = 5;
    private static final int CONNECTION_FAILED_HTTP = 503;

    private final DatasourceMapper datasourceMapper;
    private final SiteMapper siteMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DatasourceService(DatasourceMapper datasourceMapper, SiteMapper siteMapper) {
        this.datasourceMapper = datasourceMapper;
        this.siteMapper = siteMapper;
    }

    public List<DatasourceResponse> list(String siteId) {
        if (siteId == null || siteId.isBlank()) {
            throw BusinessException.invalidArgument("siteId is required");
        }
        if (siteMapper.getById(siteId) == null) throw BusinessException.siteNotFound();
        return datasourceMapper.listBySiteId(siteId).stream()
                .map(DatasourceResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public DatasourceResponse get(String id) {
        Datasource d = datasourceMapper.getById(id);
        if (d == null) throw BusinessException.datasourceNotFound();
        return DatasourceResponse.fromEntity(d);
    }

    public DatasourceResponse create(String siteId, String name, String type, JsonNode config) {
        validateType(type);
        if (siteMapper.getById(siteId) == null) throw BusinessException.siteNotFound();
        Datasource ds = new Datasource();
        ds.setId(UUID.randomUUID().toString());
        ds.setSiteId(siteId);
        ds.setName(name);
        ds.setType(type);
        ds.setConfigJson(configToJson(config));
        Instant now = Instant.now();
        ds.setCreatedAt(now);
        ds.setUpdatedAt(now);
        try {
            datasourceMapper.insert(ds);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicate(e)) throw BusinessException.datasourceNameConflict();
            throw e;
        }
        return DatasourceResponse.fromEntity(ds);
    }

    public DatasourceResponse update(String id, String siteId, String name, String type, JsonNode config) {
        validateType(type);
        Datasource ds = datasourceMapper.getById(id);
        if (ds == null) throw BusinessException.datasourceNotFound();
        if (siteId != null && !siteId.isBlank()) ds.setSiteId(siteId);
        if (siteMapper.getById(ds.getSiteId()) == null) throw BusinessException.siteNotFound();
        ds.setName(name);
        ds.setType(type);
        ds.setConfigJson(configToJson(config));
        ds.setUpdatedAt(Instant.now());
        try {
            int n = datasourceMapper.update(ds);
            if (n == 0) throw BusinessException.datasourceNotFound();
        } catch (DataIntegrityViolationException e) {
            if (isDuplicate(e)) throw BusinessException.datasourceNameConflict();
            throw e;
        }
        return DatasourceResponse.fromEntity(ds);
    }

    public void delete(String id) {
        int n = datasourceMapper.deleteById(id);
        if (n == 0) throw BusinessException.datasourceNotFound();
    }

    /**
     * Probe the datasource. For {@code api} type, issues a GET to {@code config.url}
     * (headers optional) with a 5s cap; any non-2xx or IO failure becomes a
     * {@code DATASOURCE_CONNECTION_FAILED} (503). For {@code static} type returns ok
     * immediately. Latency is measured around the HTTP call.
     *
     * <p>Security: this backend call is server-to-server; SSRF protection for
     * user-driven fetches lives in the BFF (see bff proxy/fetch route). Here we only
     * validate the configured URL parses; admins are trusted to configure legit
     * targets.
     */
    public DatasourceTestResult testConnection(String id) {
        Datasource ds = datasourceMapper.getById(id);
        if (ds == null) throw BusinessException.datasourceNotFound();
        if ("static".equals(ds.getType())) {
            return new DatasourceTestResult(true, "static datasource: no remote to probe", 0L);
        }
        JsonNode config = parseConfig(ds.getConfigJson());
        String url = config != null ? config.path("url").asText(null) : null;
        if (url == null || url.isBlank()) {
            throw BusinessException.datasourceConnectionFailed("config.url is required for api datasource");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw BusinessException.datasourceConnectionFailed("invalid config.url: " + e.getMessage());
        }
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(TEST_TIMEOUT_SECONDS))
                .GET();
        JsonNode headersNode = config.path("headers");
        if (headersNode.isObject()) {
            var fields = headersNode.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String value = entry.getValue().isTextual() ? entry.getValue().asText() : entry.getValue().toString();
                reqBuilder.header(entry.getKey(), value);
            }
        }
        long start = System.currentTimeMillis();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TEST_TIMEOUT_SECONDS))
                .build();
        try {
            HttpResponse<Void> resp = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.discarding());
            long latency = System.currentTimeMillis() - start;
            int sc = resp.statusCode();
            if (sc >= 200 && sc < 400) {
                return new DatasourceTestResult(true, "HTTP " + sc, latency);
            }
            throw BusinessException.datasourceConnectionFailed("HTTP " + sc);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.datasourceConnectionFailed(e.getMessage() != null ? e.getMessage() : "connection failed");
        }
    }

    private void validateType(String type) {
        if (type == null || !ALLOWED_TYPES.contains(type)) {
            throw BusinessException.invalidArgument("type must be one of " + ALLOWED_TYPES);
        }
    }

    private boolean isDuplicate(DataIntegrityViolationException e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("Duplicate") || msg.contains("Unique index") || msg.contains("uk_datasources_site_name"));
    }

    private String configToJson(JsonNode config) {
        if (config == null) return "{}";
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            return "{}";
        }
    }

    private JsonNode parseConfig(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
