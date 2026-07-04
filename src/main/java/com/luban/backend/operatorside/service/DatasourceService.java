package com.luban.backend.operatorside.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.shared.domain.DatasourceAggregate;
import com.luban.backend.shared.dto.DatasourceResponse;
import com.luban.backend.shared.dto.DatasourceTestResult;
import com.luban.backend.shared.entity.Datasource;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.SiteMapper;
import com.luban.backend.shared.repository.DatasourceRepository;
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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 数据源应用服务（backend-ddd-refactor plan v2 T11）。
 *
 * <p>用例编排：加载聚合根 → 调聚合根方法 → 保存。
 * 业务不变量（type 白名单）已下沉到 {@link DatasourceAggregate}。
 *
 * <p><b>留在本 Service 的逻辑（属 infra，不进聚合根）</b>：
 * <ul>
 *   <li>{@code testConnection}：HTTP GET 探测（网络 IO，聚合根零 infra 依赖）</li>
 *   <li>{@code configToJson}：JsonNode→String 序列化（依赖 ObjectMapper）</li>
 *   <li>{@code isDuplicate} 翻译：DataIntegrityViolationException→DATASOURCE_NAME_CONFLICT</li>
 *   <li>SITE_NOT_FOUND 校验：跨聚合查询（SiteMapper，对齐 TemplateService 范式）</li>
 * </ul>
 *
 * <p>持久化经 {@link DatasourceRepository}（不直接依赖 Mapper 做 CRUD，
 * 但 {@code testConnection} 的 getById 用 Mapper 直接读——testConnection 无 siteId 参数，
 * 是 admin 内部路径，且需保持"无 @Transactional"约束，故直接用 Mapper 读取避免事务注解干扰）。
 *
 * <p>多租户守卫回退：siteId 为空时回退 id-only（admin 兼容路径，{@code DatasourceContractTest} 锁定）。
 *
 * <p>Aligned with luban-backend-go (same type whitelist, error codes, TestConnection shape).
 */
@Service
public class DatasourceService {

    private static final Logger log = LoggerFactory.getLogger(DatasourceService.class);

    private static final int TEST_TIMEOUT_SECONDS = 5;

    private final DatasourceRepository datasourceRepository;
    // SiteMapper 用于 SITE_NOT_FOUND 跨聚合校验（对齐 TemplateService 范式，
    // Site 是种子数据查询，非聚合根写不变量，保留在 Service 层）。
    private final SiteMapper siteMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DatasourceService(DatasourceRepository datasourceRepository,
                             SiteMapper siteMapper) {
        this.datasourceRepository = datasourceRepository;
        this.siteMapper = siteMapper;
    }

    @Transactional(readOnly = true)
    public List<DatasourceResponse> list(String siteId) {
        if (siteId == null || siteId.isBlank()) {
            throw BusinessException.invalidArgument("siteId is required");
        }
        if (siteMapper.getById(siteId) == null) throw BusinessException.siteNotFound();
        return datasourceRepository.listBySiteId(siteId).stream()
                .map(DatasourceResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DatasourceResponse get(String id, String siteId) {
        DatasourceAggregate agg = datasourceRepository.findById(id, siteId);
        if (agg == null) throw BusinessException.datasourceNotFound();
        return DatasourceResponse.fromEntity(agg.toEntity());
    }

    @Transactional(rollbackFor = Exception.class)
    public DatasourceResponse create(String siteId, String name, String type, JsonNode config) {
        if (siteMapper.getById(siteId) == null) throw BusinessException.siteNotFound();
        // type 白名单由聚合根工厂校验（非法值抛 INVALID_ARGUMENT）
        DatasourceAggregate agg = DatasourceAggregate.newDatasource(
                UUID.randomUUID().toString(), siteId, name, type, configToJson(config, true));
        try {
            datasourceRepository.save(agg);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicate(e)) {
                log.warn("datasource create rejected: name conflict (siteId={}, name={}, type={})", siteId, name, type);
                throw BusinessException.datasourceNameConflict();
            }
            throw e;
        }
        log.info("datasource created (id={}, siteId={}, name={}, type={})",
                agg.toEntity().getId(), siteId, name, type);
        return DatasourceResponse.fromEntity(agg.toEntity());
    }

    @Transactional(rollbackFor = Exception.class)
    public DatasourceResponse update(String id, String siteId, String name, String type, JsonNode config) {
        DatasourceAggregate agg = datasourceRepository.findById(id, siteId);
        if (agg == null) throw BusinessException.datasourceNotFound();
        // type 白名单由聚合根方法校验（非法值抛 INVALID_ARGUMENT，聚合根状态不变）
        agg.update(name, type, configToJson(config, true));
        try {
            datasourceRepository.save(agg);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicate(e)) {
                log.warn("datasource update rejected: name conflict (id={}, name={})", id, name);
                throw BusinessException.datasourceNameConflict();
            }
            throw e;
        }
        Datasource saved = agg.toEntity();
        log.info("datasource updated (id={}, siteId={}, name={}, type={})", id, saved.getSiteId(), name, type);
        return DatasourceResponse.fromEntity(saved);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String id, String siteId) {
        int n = datasourceRepository.delete(id, siteId);
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
        DatasourceAggregate agg = datasourceRepository.findByIdAdmin(id);
        if (agg == null) throw BusinessException.datasourceNotFound();
        if (agg.isStaticType()) {
            return new DatasourceTestResult(true, "static datasource: no remote to probe", 0L);
        }
        JsonNode config = parseConfig(agg.getConfigJson());
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
