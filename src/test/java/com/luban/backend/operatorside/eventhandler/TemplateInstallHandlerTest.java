package com.luban.backend.operatorside.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.shared.domain.event.TemplateInstalledEvent;
import com.luban.backend.shared.entity.Page;
import com.luban.backend.shared.entity.TemplateInstallation;
import com.luban.backend.shared.mapper.PageMapper;
import com.luban.backend.shared.mapper.TemplateInstallationMapper;
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
 * TemplateInstallHandler 单测（backend-ddd-refactor plan v2 T4）。
 *
 * <p>验证 TemplateInstalledEvent → 创建 draft Page 链路。
 * 锁定 Page 实体字段赋值（name 非 title，对齐 pages 表 schema），状态为 draft。
 */
@ExtendWith(MockitoExtension.class)
class TemplateInstallHandlerTest {

    @Mock private PageMapper pageMapper;
    @Mock private TemplateInstallationMapper installationMapper;

    private TemplateInstallHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TemplateInstallHandler(pageMapper, installationMapper);
    }

    @Test
    void createsDraftPageAndInstallationFromTemplateEvent() throws Exception {
        ObjectMapper om = new ObjectMapper();
        JsonNode schema = om.readTree("{\"components\":[]}");
        TemplateInstalledEvent event = new TemplateInstalledEvent(
                "tmpl-1", "site-1", "落地页A", "/landing-a", schema, Instant.now());

        handler.on(event);

        ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(pageMapper).insert(pageCaptor.capture());
        Page saved = pageCaptor.getValue();
        assertThat(saved.getSiteId()).isEqualTo("site-1");
        assertThat(saved.getPath()).isEqualTo("/landing-a");
        assertThat(saved.getName()).isEqualTo("落地页A");   // name 字段（非 title）
        assertThat(saved.getStatus()).isEqualTo("draft");
        assertThat(saved.getSchemaJson()).isEqualTo("{\"components\":[]}");

        // installation 审计行也由 handler 写入（pageId 同步可得）
        ArgumentCaptor<TemplateInstallation> instCaptor = ArgumentCaptor.forClass(TemplateInstallation.class);
        verify(installationMapper).insert(instCaptor.capture());
        TemplateInstallation inst = instCaptor.getValue();
        assertThat(inst.getTemplateId()).isEqualTo("tmpl-1");
        assertThat(inst.getSiteId()).isEqualTo("site-1");
        assertThat(inst.getPageId()).isEqualTo(saved.getId());
    }

    @Test
    void defaultsSchemaToEmptyObjectJsonWhenNull() {
        TemplateInstalledEvent event = new TemplateInstalledEvent(
                "tmpl-1", "site-1", "空模板", "/x", null, Instant.now());

        handler.on(event);

        ArgumentCaptor<Page> captor = ArgumentCaptor.forClass(Page.class);
        verify(pageMapper).insert(captor.capture());
        assertThat(captor.getValue().getSchemaJson()).isEqualTo("{}");
    }
}
