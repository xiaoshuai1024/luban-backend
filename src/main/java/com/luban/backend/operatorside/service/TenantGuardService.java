package com.luban.backend.operatorside.service;

import com.luban.backend.shared.auth.UserContext;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.port.SiteMembershipPort;
import org.springframework.stereotype.Service;

/**
 * 多租户授权守卫（🟡 tenant authz 修复）。
 * <p>admin 角色可访问所有站点；非 admin 用户仅能访问 user_sites 中已授权的站点。
 * <p>由各 service 的 admin 端点（list/get/export/transit/contact）在校验 siteId 存在后调用。
 * <p>授权映射查询经 {@link SiteMembershipPort}（依赖倒置），不直连 UserSiteMapper。
 */
@Service
public class TenantGuardService {

    private final SiteMembershipPort siteMembership;

    public TenantGuardService(SiteMembershipPort siteMembership) {
        this.siteMembership = siteMembership;
    }

    /**
     * 校验当前用户是否有权访问指定站点。
     * 无 X-User-ID（公开端点）直接放行；admin 放行；非 admin 查 user_sites。
     *
     * @throws BusinessException PERMISSION_DENIED 无权访问
     */
    public void ensureSiteAccess(String siteId) {
        if (siteId == null || siteId.isBlank()) return;
        String userId = UserContext.getUserId();
        // 公开端点（无 X-User-ID）或未注入角色，放行（由调用方另做公开性校验）
        if (userId == null || userId.isBlank()) return;
        if (UserContext.isAdmin()) return; // admin 全站访问
        // 非 admin：必须有 user_sites 授权记录
        if (!siteMembership.existsMembership(userId, siteId)) {
            throw BusinessException.permissionDenied();
        }
    }
}
