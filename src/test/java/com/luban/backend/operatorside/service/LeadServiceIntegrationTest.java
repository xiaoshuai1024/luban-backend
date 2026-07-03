package com.luban.backend.operatorside.service;
import com.luban.backend.operatorside.service.LeadService;

import com.luban.backend.shared.dto.LeadSubmitRequest;
import com.luban.backend.shared.dto.LeadSubmitResult;
import com.luban.backend.shared.entity.Form;
import com.luban.backend.shared.entity.Lead;
import com.luban.backend.shared.entity.Page;
import com.luban.backend.shared.entity.Site;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.FormMapper;
import com.luban.backend.shared.mapper.LeadMapper;
import com.luban.backend.shared.mapper.PageMapper;
import com.luban.backend.shared.mapper.SiteMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LeadService 端到端集成测试：真实 MySQL（schema.sql 建表）+ 真实 Redis（防刷频控）。
 * 覆盖留资提交→入库→加密、去重拒绝。@Transactional 自动回滚 DB；Redis 用唯一 IP 规避计数残留。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class LeadServiceIntegrationTest {

    @Autowired private LeadService leadService;
    @Autowired private FormMapper formMapper;
    @Autowired private SiteMapper siteMapper;
    @Autowired private PageMapper pageMapper;
    @Autowired private LeadMapper leadMapper;

    /** 构造 site/page/form 前置数据，返回 formId。 */
    private String seed() {
        Instant now = Instant.now();
        String unique = UUID.randomUUID().toString();
        Site site = new Site();
        site.setId(unique);
        site.setName("test-site");
        site.setSlug("slug-" + unique.substring(0, 8));
        site.setStatus("active");
        site.setCreatedAt(now);
        site.setUpdatedAt(now);
        siteMapper.insert(site);

        Page page = new Page();
        page.setId(UUID.randomUUID().toString());
        page.setSiteId(site.getId());
        page.setName("landing");
        page.setPath("/p-" + unique.substring(0, 8));
        page.setStatus("published");
        page.setSchemaJson("{}");
        page.setCreatedAt(now);
        page.setUpdatedAt(now);
        pageMapper.insert(page);

        Form form = new Form();
        form.setId(UUID.randomUUID().toString());
        form.setSiteId(site.getId());
        form.setPageId(page.getId());
        form.setName("signup");
        form.setFieldSchemaJson("[]");
        form.setSubmitConfigJson("{}");
        form.setDedupKeysJson("[\"phone\"]");
        form.setDedupWindow(86400);
        form.setDedupPolicy("reject");
        form.setStatus("active");
        form.setCreatedAt(now);
        form.setUpdatedAt(now);
        formMapper.insert(form);
        return form.getId();
    }

    private String uniqueIp() {
        return "10.0.0." + (UUID.randomUUID().hashCode() & 0x7F);
    }

    @Test
    void submitPersistsLeadAndEncryptsContact() {
        String formId = seed();
        Form form = formMapper.getById(formId);

        LeadSubmitRequest req = new LeadSubmitRequest(
                formId, Map.of("phone", "13800099999", "name", "张三"),
                null, null, null, uniqueIp(), "visitor-" + formId, null);
        LeadSubmitResult result = leadService.submit(req);

        assertThat(result.leadId()).isNotBlank();
        assertThat(result.status()).isEqualTo("new");
        assertThat(result.dedup()).isFalse();

        // 验证入库 + contact 加密（非明文）
        Lead persisted = leadMapper.getByIdAndSiteId(result.leadId(), form.getSiteId());
        assertThat(persisted).isNotNull();
        assertThat(persisted.getContactJson()).isNotEqualTo("{\"phone\":\"13800099999\"}");
        assertThat(persisted.getContactJson()).doesNotContain("13800099999");
        assertThat(persisted.getDedupHash()).hasSize(64);
    }

    @Test
    void submitRejectsDuplicateByPhone() {
        String formId = seed();
        String ip = uniqueIp();
        LeadSubmitRequest req = new LeadSubmitRequest(
                formId, Map.of("phone", "13900088888"), null, null, null, ip, "v-dup", null);

        LeadSubmitResult first = leadService.submit(req);
        assertThat(first.status()).isEqualTo("new");

        // 同 phone 再提交（不同 visitor，但去重键 phone 相同 → 命中）
        LeadSubmitRequest dup = new LeadSubmitRequest(
                formId, Map.of("phone", "13900088888"), null, null, null, ip, "v-dup2", null);
        assertThatThrownBy(() -> leadService.submit(dup))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("LEAD_DUPLICATE");
    }

    @Test
    void listReturnsCreatedLeadWithMaskedContact() {
        String formId = seed();
        Form form = formMapper.getById(formId);
        leadService.submit(new LeadSubmitRequest(
                formId, Map.of("phone", "13800011111"), null, null, null, uniqueIp(), "v-list", null));

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> page = leadService.list(form.getSiteId(), null, null, null, null, 1, 20);
        assertThat(((Number) page.get("total")).intValue()).isGreaterThan(0);

        @SuppressWarnings("unchecked")
        java.util.List<com.luban.backend.shared.dto.LeadResponse> respList =
                (java.util.List<com.luban.backend.shared.dto.LeadResponse>) page.get("list");
        assertThat(respList).isNotEmpty();
        // contactMasked 应存在（phone 已脱敏）
        assertThat(respList.get(0).contactMasked()).isNotNull();
    }
}
