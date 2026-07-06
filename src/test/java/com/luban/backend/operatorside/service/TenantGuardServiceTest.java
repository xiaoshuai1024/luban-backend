package com.luban.backend.operatorside.service;

import com.luban.backend.shared.auth.UserContext;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.port.SiteMembershipPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TenantGuardService 单测（backend-ddd-refactor plan v2 T16，补覆盖率）。
 *
 * <p>覆盖 {@link TenantGuardService#ensureSiteAccess} 的 5 个分支：
 * siteId null 早退 / 公开端点（无 user）早退 / admin 放行 / 非 admin 且无授权 PERMISSION_DENIED /
 * 非 admin 且有授权通过。
 *
 * <p>UserContext 是 ThreadLocal 静态方法——测试直接 {@link UserContext#set} / {@link UserContext#clear}
 * 设置，无需 mockStatic（ThreadLocal 天然线程隔离，且语义即「当前线程用户」）。
 */
@ExtendWith(MockitoExtension.class)
class TenantGuardServiceTest {

    @Mock private SiteMembershipPort siteMembership;

    private TenantGuardService guard;

    @BeforeEach
    void setUp() {
        guard = new TenantGuardService(siteMembership);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();    // 清理 ThreadLocal，防测试间污染
    }

    @Test
    void ensureSiteAccess_returns_early_when_siteId_null() {
        UserContext.set("user-1", "user");

        assertThatCode(() -> guard.ensureSiteAccess(null)).doesNotThrowAnyException();
        // siteId null 直接 return，不查 port
        verify(siteMembership, never()).existsMembership(anyString(), anyString());
    }

    @Test
    void ensureSiteAccess_returns_early_when_siteId_blank() {
        UserContext.set("user-1", "user");

        assertThatCode(() -> guard.ensureSiteAccess("   ")).doesNotThrowAnyException();
        verify(siteMembership, never()).existsMembership(anyString(), anyString());
    }

    @Test
    void ensureSiteAccess_returns_early_for_public_endpoint_no_user() {
        // 公开端点（如短链解析）无用户上下文 → 不校验
        UserContext.clear();   // 无 user

        assertThatCode(() -> guard.ensureSiteAccess("site-1")).doesNotThrowAnyException();
        verify(siteMembership, never()).existsMembership(anyString(), anyString());
    }

    @Test
    void ensureSiteAccess_allows_admin_without_existence_check() {
        UserContext.set("admin-1", "admin");

        assertThatCode(() -> guard.ensureSiteAccess("site-1")).doesNotThrowAnyException();
        // admin 直接放行，不查授权
        verify(siteMembership, never()).existsMembership(anyString(), anyString());
    }

    @Test
    void ensureSiteAccess_throws_when_non_admin_without_authorization() {
        UserContext.set("user-1", "user");
        when(siteMembership.existsMembership("user-1", "site-1")).thenReturn(false);

        assertThatThrownBy(() -> guard.ensureSiteAccess("site-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("PERMISSION_DENIED");
    }

    @Test
    void ensureSiteAccess_allows_non_admin_with_authorization() {
        UserContext.set("user-1", "user");
        when(siteMembership.existsMembership("user-1", "site-1")).thenReturn(true);

        assertThatCode(() -> guard.ensureSiteAccess("site-1")).doesNotThrowAnyException();
    }
}
