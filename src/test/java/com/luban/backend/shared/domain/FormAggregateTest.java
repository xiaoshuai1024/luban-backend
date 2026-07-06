package com.luban.backend.shared.domain;

import com.luban.backend.shared.entity.Form;
import com.luban.backend.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FormAggregate 单测（backend-ddd-refactor plan v2 T8）。
 *
 * <p>锁定真聚合根范式不变量：
 * <ul>
 *   <li>dedupPolicy 枚举白名单：reject/mark/overwrite/merge（对齐 DedupService.Policy）</li>
 *   <li>status 枚举白名单：active/disabled</li>
 *   <li>工厂 newForm：默认 dedupPolicy=reject、status=active、dedupWindow=86400</li>
 *   <li>update：patch 语义（null 保留原值）</li>
 *   <li>assertDeletable：有线索抛 FORM_HAS_LEADS（跨聚合查询由 Service 完成后传入）</li>
 * </ul>
 *
 * <p>"有线索" 是跨聚合查询（Lead 域），聚合根零跨聚合依赖：Service 查询 countByFormId 后
 * 传 boolean 给 {@link FormAggregate#assertDeletable(boolean)}，聚合根负责断言决策。
 */
class FormAggregateTest {

    @Test
    void newFormAppliesDefaults() {
        Instant before = Instant.now();
        FormAggregate agg = FormAggregate.newForm(
                "f-1", "site-1", "page-1", "联系表单",
                "{}", "{}", "[]", null, null, null, null);

        Form f = agg.toEntity();
        assertThat(f.getId()).isEqualTo("f-1");
        assertThat(f.getSiteId()).isEqualTo("site-1");
        assertThat(f.getName()).isEqualTo("联系表单");
        assertThat(f.getDedupPolicy()).isEqualTo("reject");   // 默认
        assertThat(f.getStatus()).isEqualTo("active");        // 默认
        assertThat(f.getDedupWindow()).isEqualTo(86400);      // 默认 1 天
        assertThat(f.getCreatedAt()).isBetween(before, Instant.now().plusSeconds(1));
        assertThat(f.getUpdatedAt()).isEqualTo(f.getCreatedAt());
    }

    @Test
    void newFormRejectsInvalidDedupPolicy() {
        assertThatThrownBy(() -> FormAggregate.newForm(
                "f-1", "site-1", "page-1", "n", "{}", null, null, null, "weird", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dedupPolicy");
    }

    @Test
    void newFormRejectsInvalidStatus() {
        assertThatThrownBy(() -> FormAggregate.newForm(
                "f-1", "site-1", "page-1", "n", "{}", null, null, null, null, null, "frozen"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("status");
    }

    @Test
    void newFormAcceptsAllValidDedupPolicies() {
        for (String p : new String[]{"reject", "mark", "overwrite", "merge"}) {
            FormAggregate agg = FormAggregate.newForm(
                    "f", "s", "p", "n", "{}", null, null, null, p, null, null);
            assertThat(agg.toEntity().getDedupPolicy()).isEqualTo(p);
        }
    }

    @Test
    void reconstitutePreservesPersistedState() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Form persisted = new Form();
        persisted.setId("f-9");
        persisted.setSiteId("site-1");
        persisted.setPageId("page-1");
        persisted.setName("旧表单");
        persisted.setDedupPolicy("merge");
        persisted.setStatus("disabled");
        persisted.setDedupWindow(3600);
        persisted.setCreatedAt(created);
        persisted.setUpdatedAt(created);

        FormAggregate agg = FormAggregate.reconstitute(persisted);

        Form f = agg.toEntity();
        assertThat(f.getDedupPolicy()).isEqualTo("merge");
        assertThat(f.getStatus()).isEqualTo("disabled");
        assertThat(f.getName()).isEqualTo("旧表单");
        assertThat(agg.pullEvents()).isEmpty();
    }

    @Test
    void updatePatchesNonNullFieldsOnly() {
        FormAggregate agg = FormAggregate.newForm(
                "f-1", "site-1", "page-1", "原名", "{}", "{}", "[]", 86400, "reject", "{}", "active");

        // 只改 name 和 dedupPolicy，其余传 null 保留原值
        agg.update("新名", null, null, null, null, "merge", null, null);

        Form f = agg.toEntity();
        assertThat(f.getName()).isEqualTo("新名");
        assertThat(f.getDedupPolicy()).isEqualTo("merge");
        assertThat(f.getStatus()).isEqualTo("active");        // 保留
        assertThat(f.getSubmitConfigJson()).isEqualTo("{}");  // 保留
        assertThat(f.getDedupWindow()).isEqualTo(86400);      // 保留
    }

    @Test
    void updateRejectsInvalidDedupPolicy() {
        FormAggregate agg = FormAggregate.newForm(
                "f-1", "site-1", "page-1", "n", "{}", null, null, null, "reject", null, null);

        assertThatThrownBy(() -> agg.update("n", null, null, null, null, "bogus", null, null))
                .isInstanceOf(BusinessException.class);

        // 抛异常后聚合根状态不变
        assertThat(agg.toEntity().getDedupPolicy()).isEqualTo("reject");
    }

    @Test
    void updateRejectsInvalidStatus() {
        FormAggregate agg = FormAggregate.newForm(
                "f-1", "site-1", "page-1", "n", "{}", null, null, null, "reject", null, null);

        assertThatThrownBy(() -> agg.update("n", null, null, null, null, null, null, "ghost"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void assertDeletablePassesWhenNoLeads() {
        FormAggregate agg = FormAggregate.newForm(
                "f-1", "site-1", "page-1", "n", "{}", null, null, null, null, null, null);

        agg.assertDeletable(false);   // 无线索，不抛异常
    }

    @Test
    void assertDeletableThrowsWhenHasLeads() {
        FormAggregate agg = FormAggregate.newForm(
                "f-1", "site-1", "page-1", "n", "{}", null, null, null, null, null, null);

        assertThatThrownBy(() -> agg.assertDeletable(true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("线索");
    }

    @Test
    void disableTransitionsActiveToDisabled() {
        FormAggregate agg = FormAggregate.newForm(
                "f-1", "site-1", "page-1", "n", "{}", null, null, null, null, null, null);
        assertThat(agg.toEntity().getStatus()).isEqualTo("active");

        agg.disable();

        assertThat(agg.toEntity().getStatus()).isEqualTo("disabled");
    }

    @Test
    void enableTransitionsDisabledToActive() {
        Form persisted = new Form();
        persisted.setId("f-2");
        persisted.setStatus("disabled");
        persisted.setDedupPolicy("reject");
        FormAggregate agg = FormAggregate.reconstitute(persisted);

        agg.enable();

        assertThat(agg.toEntity().getStatus()).isEqualTo("active");
    }
}
