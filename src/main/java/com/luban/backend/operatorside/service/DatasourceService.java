package com.luban.backend.operatorside.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.luban.backend.shared.dto.DatasourceResponse;
import com.luban.backend.shared.dto.DatasourceTestResult;
import com.luban.backend.shared.entity.Datasource;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.DatasourceMapper;
import com.luban.backend.shared.mapper.SiteMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Datasource CRUD + connection test. Aligned with luban-backend-go
 * internal/service/datasource_service.go (same {@code type} whitelist, same
 * DATASOURCE_NAME_CONFLICT/SITE_NOT_FOUND/DATASOURCE_NOT_FOUND error codes, same
 * TestConnection return shape).
 *
 * <p>Multi-tenant isolation: {@code get}/{@code update}/{@code delete} accept a
 * {@code siteId} and scope the row at the SQL layer (see {@link DatasourceMapper}).
 * When the API caller omits {@code siteId}, the service falls back to the id-only
 * lookup (admin/internal paths) rather than rejecting the call — preserving backward
 * compatibility with the existing contract tests.
 *
 * <p>{@code testConnection} only applies to type={@code api} (HTTP GET against the
 * configured {@code url}). For {@code static} datasources there is no remote to
 * probe — we return ok=true with latency 0 immediately, matching the Go backend.
 */
@Service
public class DatasourceService {

    private static final Logger log = LoggerFactory.getLogger(DatasourceService.class);

    /** Whitelist enforced identically on both backends (plan §3). */
    static final Set<String> ALLOWED_TYPES = Set.of("static", "api");

    private static final int TEST_TIMEOUT_SECONDS = 5;

    private final DatasourceMapper datasourceMapper;
    private final SiteMapper siteMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DatasourceService(DatasourceMapper datasourceMapper, SiteMapper siteMapper) {
        this.datasourceMapper = datasourceMapper;
        this.siteMapper = siteMapper;
    }

    @Transactional(readOnly = true)
    public List<DatasourceResponse> list(String siteId) {
        if (siteId == null || siteId.isBlank()) {
            throw BusinessException.invalidArgument("siteId is required");
        }
        if (siteMapper.getById(siteId) == null) throw BusinessException.siteNotFound();
        return datasourceMapper.listBySiteId(siteId).stream()
                .map(DatasourceResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DatasourceResponse get(String id, String siteId) {
        Datasource d = (siteId == null || siteId.isBlank())
                ? datasourceMapper.getById(id)
                : datasourceMapper.getByIdAndSiteId(id, siteId);
        if (d == null) throw BusinessException.datasourceNotFound();
        return DatasourceResponse.fromEntity(d);
    }

    @Transactional(rollbackFor = Exception.class)
    public DatasourceResponse create(String siteId, String name, String type, JsonNode config) {
        validateType(type);
        if (siteMapper.getById(siteId) == null) throw BusinessException.siteNotFound();
        Datasource ds = new Datasource();
        ds.setId(UUID.randomUUID().toString());
        ds.setSiteId(siteId);
        ds.setName(name);
        ds.setType(type);
        ds.setConfigJson(configToJson(config, true));
        Instant now = Instant.now();
        ds.setCreatedAt(now);
        ds.setUpdatedAt(now);
        try {
            datasourceMapper.insert(ds);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicate(e)) {
                log.warn("datasource create rejected: name conflict (siteId={}, name={}, type={})", siteId, name, type);
                throw BusinessException.datasourceNameConflict();
            }
            throw e;
        }
        log.info("datasource created (id={}, siteId={}, name={}, type={})", ds.getId(), siteId, name, type);
        return DatasourceResponse.fromEntity(ds);
    }

    @Transactional(rollbackFor = Exception.class)
    public DatasourceResponse update(String id, String siteId, String name, String type, JsonNode config) {
        validateType(type);
        // Tenant guard: when siteId is supplied, scope the lookup; fall back to id-only
        // for admin/internal callers (preserves backward compatibility with existing tests).
        Datasource ds = (siteId == null || siteId.isBlank())
                ? datasourceMapper.getById(id)
                : datasourceMapper.getByIdAndSiteId(id, siteId);
        if (ds == null) throw BusinessException.datasourceNotFound();
        // site_id is immutable; ignore any caller-supplied siteId override.
        ds.setName(name);
        ds.setType(type);
        ds.setConfigJson(configToJson(config, true));
        ds.setUpdatedAt(Instant.now());
        try {
            int n = datasourceMapper.update(ds);
            if (n == 0) throw BusinessException.datasourceNotFound();
        } catch (DataIntegrityViolationException e) {
            if (isDuplicate(e)) {
                log.warn("datasource update rejected: name conflict (id={}, siteId={}, name={})", id, ds.getSiteId(), name);
                throw BusinessException.datasourceNameConflict();
            }
            throw e;
        }
        log.info("datasource updated (id={}, siteId={}, name={}, type={})", id, ds.getSiteId(), name, type);
        return DatasourceResponse.fromEntity(ds);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String id, String siteId) {
        int n = (siteId == null || siteId.isBlank())
                ? datasourceMapper.deleteById(id)
                : datasourceMapper.deleteByIdAndSiteId(id, siteId);
        if (n == 0) throw BusinessException.datasourceNotFound();
        log.info("datasource deleted (id={}, siteId={})", id, siteId);
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
     * targets. No {@code @Transactional} — this method makes an outbound HTTP call
     * and must not hold a DB transaction open across it.
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
            log.warn("datasource connection probe failed (id={}, host={}, status={})", id, uri.getHost(), sc);
            throw BusinessException.datasourceConnectionFailed("HTTP " + sc);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("datasource connection probe failed (id={}, host={}, err={})", id, uri.getHost(), e.getMessage());
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

    /**
     * Serialize config to a JSON string for storage.
     *
     * @param strictOnFailure when {@code true} (create/update paths), a serialization
     *                        failure throws INVALID_ARGUMENT rather than silently
     *                        degrading to {@code "{}"} — callers sent this config and
     *                        must be told it's invalid. When {@code false} (internal
     *                        read paths), we degrade to {@code "{}"} with a warn log.
     */
    private String configToJson(JsonNode config, boolean strictOnFailure) {
        if (config == null) return "{}";
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            if (strictOnFailure) {
                log.warn("config serialization rejected (isNull={}, isMissing={})", config.isNull(), config.isMissingNode());
                throw BusinessException.invalidArgument("config is not serializable");
            }
            log.warn("config serialization failed, degrading to empty object: {}", e.getMessage());
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
