package com.luban.backend.service;

import com.luban.backend.auth.UserContext;
import com.luban.backend.exception.BusinessException;
import com.luban.backend.mapper.UserSiteMapper;
import org.springframework.stereotype.Service;

/**
 * 多租户授权守卫（🟡 tenant authz 修复）。
 * <p>admin 角色可访问所有站点；非 admin 用户仅能访问 user_sites 中已授权的站点。
 * <p>由各 service 的 admin 端点（list/get/export/transit/contact）在校验 siteId 存在后调用。
 */
@Service
public class TenantGuardService {

    private final UserSiteMapper userSiteMapper;

    public TenantGuardService(UserSiteMapper userSiteMapper) {
        this.userSiteMapper = userSiteMapper;
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
        if (userSiteMapper.exists(userId, siteId) == 0) {
            throw BusinessException.permissionDenied();
        }
    }
}
