package com.luban.backend.shared.domain;

import com.luban.backend.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TemplateAggregate 单测 — 状态机 + 不变量校验（template-marketplace plan）。
 */
class TemplateAggregateTest {

    // === 类目/slug 校验 ===

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
        assertThrows(BusinessException.class, () -> TemplateAggregate.validateSlug("has space"));
        assertThrows(BusinessException.class, () -> TemplateAggregate.validateSlug(null));
    }

    // === 状态机 ===

    @Test
    void transitionStatus_draft发布() {
        assertEquals("published", TemplateAggregate.transitionStatus("draft", "published"));
    }

    @Test
    void transitionStatus_published归档() {
        assertEquals("archived", TemplateAggregate.transitionStatus("published", "archived"));
    }

    @Test
    void transitionStatus_published推荐() {
        assertEquals("featured", TemplateAggregate.transitionStatus("published", "featured"));
    }

    @Test
    void transitionStatus_featured取消推荐回published() {
        assertEquals("published", TemplateAggregate.transitionStatus("featured", "published"));
    }

    @Test
    void transitionStatus_archived重新上架() {
        assertEquals("published", TemplateAggregate.transitionStatus("archived", "published"));
    }

    @Test
    void transitionStatus_draft不能直接featured() {
        assertThrows(BusinessException.class, () -> TemplateAggregate.transitionStatus("draft", "featured"));
    }

    @Test
    void transitionStatus_archived不能直接featured() {
        assertThrows(BusinessException.class, () -> TemplateAggregate.transitionStatus("archived", "featured"));
    }

    @Test
    void transitionStatus_draft不能转draft() {
        assertThrows(BusinessException.class, () -> TemplateAggregate.transitionStatus("draft", "draft"));
    }

    // === 市场可见性 ===

    @Test
    void isMarketplaceVisible_published和featured可见() {
        assertTrue(TemplateAggregate.isMarketplaceVisible("published"));
        assertTrue(TemplateAggregate.isMarketplaceVisible("featured"));
        assertFalse(TemplateAggregate.isMarketplaceVisible("draft"));
        assertFalse(TemplateAggregate.isMarketplaceVisible("archived"));
    }
}
