package com.luban.backend.shared.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.shared.entity.Template;
import com.luban.backend.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * TemplateAggregate 单测（backend-ddd-refactor plan v2 T13，重写静态类为真聚合根）。
 *
 * <p>覆盖实例方法（newTemplate/publish/archive/feature/updateSchema/install）
 * + 保留的无状态静态校验（validateCategory/validateSlug/isMarketplaceVisible）。
 */
class TemplateAggregateTest {

    // === 静态校验（保留） ===

    @Test
    void validateCategory_合法类目通过() {
        assertDoesNotThrow(() -> TemplateAggregate.validateCategory("saas"));
        assertDoesNotThrow(() -> TemplateAggregate.validateCategory("ecommerce"));
        assertDoesNotThrow(() -> TemplateAggregate.validateCategory("blank"));
    }

    @Test
    void validateCategory_非法类目抛异常() {
        assertThrows(BusinessException.class, () -> TemplateAggregate.validateCategory("invalid"));
        assertThrows(BusinessException.class, () -> TemplateAggregate.validateCategory(null));
    }

    @Test
    void validateSlug_合法slug通过() {
        assertDoesNotThrow(() -> TemplateAggregate.validateSlug("saas-landing"));
        assertDoesNotThrow(() -> TemplateAggregate.validateSlug("my_template_1"));
    }

    @Test
    void validateSlug_非法slug抛异常() {
        assertThrows(BusinessException.class, () -> TemplateAggregate.validateSlug("含中文"));
        assertThrows(BusinessException.class, () -> TemplateAggregate.validateSlug(null));
    }

    @Test
    void isMarketplaceVisible_published和featured可见() {
        assertThat(TemplateAggregate.isMarketplaceVisible("published")).isTrue();
        assertThat(TemplateAggregate.isMarketplaceVisible("featured")).isTrue();
        assertThat(TemplateAggregate.isMarketplaceVisible("draft")).isFalse();
        assertThat(TemplateAggregate.isMarketplaceVisible("archived")).isFalse();
    }

    // === 真聚合根：工厂 + 状态机实例方法 ===

    @Test
    void newTemplateCreatesDraftWithInitialVersion() {
        TemplateAggregate agg = TemplateAggregate.newTemplate(
                "t-1", "saas-landing", "SaaS 落地页", "saas", "desc", null,
                "u-1", "{\"x\":1}", "首版");

        Template t = agg.toTemplate();
        assertThat(t.getStatus()).isEqualTo("draft");
        assertThat(t.getLatestVersion()).isEqualTo(1);
        assertThat(t.getThumbnail()).isEqualTo("📄");   // 默认
        assertThat(agg.pendingNewVersion()).isNotNull();
        assertThat(agg.pendingNewVersion().getVersion()).isEqualTo(1);
        assertThat(agg.pullEvents()).isEmpty();
    }

    @Test
    void newTemplateRejectsBlankSchema() {
        assertThatThrownBy(() -> TemplateAggregate.newTemplate(
                "t-1", "slug", "n", "saas", null, null, null, "  ", null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void publishTransitionsDraftToPublishedWhenSchemaExists() {
        TemplateAggregate agg = TemplateAggregate.newTemplate(
                "t-1", "slug", "n", "saas", null, null, null, "{}", null);

        agg.publish(true);

        assertThat(agg.toTemplate().getStatus()).isEqualTo("published");
    }

    @Test
    void publishRejectsWhenNoSchema() {
        TemplateAggregate agg = TemplateAggregate.newTemplate(
                "t-1", "slug", "n", "saas", null, null, null, "{}", null);

        assertThatThrownBy(() -> agg.publish(false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("TEMPLATE_SCHEMA_EMPTY");
    }

    @Test
    void archiveTransitionsPublishedToArchived() {
        Template t = publishedTemplate();
        TemplateAggregate agg = TemplateAggregate.reconstitute(t);

        agg.archive();

        assertThat(agg.toTemplate().getStatus()).isEqualTo("archived");
    }

    @Test
    void featureTransitionsPublishedToFeatured() {
        Template t = publishedTemplate();
        TemplateAggregate agg = TemplateAggregate.reconstitute(t);

        agg.feature();

        assertThat(agg.toTemplate().getStatus()).isEqualTo("featured");
    }

    @Test
    void featuredBackToPublished() {
        Template t = publishedTemplate();
        t.setStatus("featured");
        TemplateAggregate agg = TemplateAggregate.reconstitute(t);

        agg.archive();
        assertThat(agg.toTemplate().getStatus()).isEqualTo("archived");
    }

    @Test
    void draftCannotDirectlyFeature() {
        TemplateAggregate agg = TemplateAggregate.newTemplate(
                "t-1", "slug", "n", "saas", null, null, null, "{}", null);

        assertThatThrownBy(() -> agg.feature())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("TEMPLATE_INVALID_TRANSITION");
    }

    @Test
    void archivedCannotDirectlyFeature() {
        Template t = publishedTemplate();
        t.setStatus("archived");
        TemplateAggregate agg = TemplateAggregate.reconstitute(t);

        assertThatThrownBy(() -> agg.feature())
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void archivedRelistToPublished() {
        Template t = publishedTemplate();
        t.setStatus("archived");
        TemplateAggregate agg = TemplateAggregate.reconstitute(t);

        agg.publish(true);

        assertThat(agg.toTemplate().getStatus()).isEqualTo("published");
    }

    @Test
    void updateSchemaProducesNewVersion() {
        Template t = publishedTemplate();
        t.setLatestVersion(2);
        TemplateAggregate agg = TemplateAggregate.reconstitute(t);

        var newVer = agg.updateSchema("{\"new\":true}", "新增组件");

        assertThat(newVer.getVersion()).isEqualTo(3);
        assertThat(agg.toTemplate().getLatestVersion()).isEqualTo(3);
        assertThat(agg.pendingNewVersion()).isEqualTo(newVer);
    }

    @Test
    void updateSchemaRejectsBlank() {
        Template t = publishedTemplate();
        TemplateAggregate agg = TemplateAggregate.reconstitute(t);

        assertThatThrownBy(() -> agg.updateSchema("  ", null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void installPublishesEventWhenMarketplaceVisible() throws Exception {
        ObjectMapper om = new ObjectMapper();
        JsonNode schema = om.readTree("{\"c\":[]}");
        Template t = publishedTemplate();
        TemplateAggregate agg = TemplateAggregate.reconstitute(t);

        agg.install(schema, "site-1", "落地页", "/landing", 1);

        var events = agg.pullEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(com.luban.backend.shared.domain.event.TemplateInstalledEvent.class);
    }

    @Test
    void installRejectsWhenNotMarketplaceVisible() throws Exception {
        ObjectMapper om = new ObjectMapper();
        TemplateAggregate agg = TemplateAggregate.newTemplate(
                "t-1", "slug", "n", "saas", null, null, null, "{}", null);   // draft 态

        assertThatThrownBy(() -> agg.install(om.readTree("{}"), "site-1", "n", "/x", 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("TEMPLATE_NOT_PUBLISHED");
    }

    private static Template publishedTemplate() {
        Template t = new Template();
        t.setId("t-1");
        t.setSlug("slug");
        t.setCategory("saas");
        t.setStatus("published");
        t.setLatestVersion(1);
        return t;
    }
}
