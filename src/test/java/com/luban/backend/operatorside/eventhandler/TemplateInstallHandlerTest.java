package com.luban.backend.operatorside.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.shared.domain.PageAggregate;
import com.luban.backend.shared.domain.event.TemplateInstalledEvent;
import com.luban.backend.shared.entity.TemplateInstallation;
import com.luban.backend.shared.mapper.TemplateInstallationMapper;
import com.luban.backend.shared.repository.PageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * TemplateInstallHandler 单测（backend-ddd-refactor plan v2 T4，G1 修复版）。
 *
 * <p>验证 TemplateInstalledEvent → 经 PageAggregate.newPage + PageRepository 创建 draft Page 链路。
 * G1 重构：handler 走聚合根 + Repository（不再直接 pageMapper.insert）；失败有 ERROR 日志。
 */
@ExtendWith(MockitoExtension.class)
class TemplateInstallHandlerTest {

    @Mock private PageRepository pageRepository;
    @Mock private TemplateInstallationMapper installationMapper;

    private TemplateInstallHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TemplateInstallHandler(pageRepository, installationMapper);
    }

    @Test
    void createsDraftPageAndInstallationFromTemplateEvent() throws Exception {
        ObjectMapper om = new ObjectMapper();
        JsonNode schema = om.readTree("{\"components\":[]}");
        // G1 修复：事件携带 String schemaJson（领域纯 POJO，不携带 Jackson JsonNode）
        // G1+ 修复：事件携带 version（BUG-H：原版漏 version，handler 写 audit 表 NULL → 整事务回滚）
        TemplateInstalledEvent event = new TemplateInstalledEvent(
                "tmpl-1", "site-1", "落地页A", "/landing-a", 3, schema.toString(), Instant.now());

        handler.on(event);

        // 经聚合根 + Repository 保存（聚合根守护不变量，状态 draft 由工厂默认）
        ArgumentCaptor<PageAggregate> pageCaptor = ArgumentCaptor.forClass(PageAggregate.class);
        verify(pageRepository).save(pageCaptor.capture());
        PageAggregate saved = pageCaptor.getValue();
        assertThat(saved.toPage().getSiteId()).isEqualTo("site-1");
        assertThat(saved.toPage().getPath()).isEqualTo("/landing-a");
        assertThat(saved.toPage().getName()).isEqualTo("落地页A");   // name 字段（非 title）
        assertThat(saved.toPage().getStatus()).isEqualTo("draft");
        assertThat(saved.toPage().getSchemaJson()).isEqualTo("{\"components\":[]}");

        // installation 审计行也由 handler 写入（pageId 同步可得）
        ArgumentCaptor<TemplateInstallation> instCaptor = ArgumentCaptor.forClass(TemplateInstallation.class);
        verify(installationMapper).insert(instCaptor.capture());
        TemplateInstallation inst = instCaptor.getValue();
        assertThat(inst.getTemplateId()).isEqualTo("tmpl-1");
        assertThat(inst.getSiteId()).isEqualTo("site-1");
        assertThat(inst.getVersion()).isEqualTo(3);   // BUG-H 回归断言：version 必须从 event 透传到 audit
        assertThat(inst.getPageId()).isEqualTo(saved.toPage().getId());
    }

    @Test
    void defaultsSchemaToEmptyObjectJsonWhenNull() {
        TemplateInstalledEvent event = new TemplateInstalledEvent(
                "tmpl-1", "site-1", "空模板", "/x", 1, null, Instant.now());

        handler.on(event);

        ArgumentCaptor<PageAggregate> captor = ArgumentCaptor.forClass(PageAggregate.class);
        verify(pageRepository).save(captor.capture());
        assertThat(captor.getValue().toPage().getSchemaJson()).isEqualTo("{}");
    }
}
