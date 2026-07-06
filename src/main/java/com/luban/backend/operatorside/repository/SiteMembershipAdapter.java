package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.mapper.UserSiteMapper;
import com.luban.backend.shared.port.SiteMembershipPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link SiteMembershipPort} 实现：封装 {@link UserSiteMapper}。
 * Port 接口在 shared，实现在 operatorside，供 TenantGuardService / LeadService / FeatureGateService 共用。
 */
@Component
public class SiteMembershipAdapter implements SiteMembershipPort {

    private final UserSiteMapper userSiteMapper;

    public SiteMembershipAdapter(UserSiteMapper userSiteMapper) {
        this.userSiteMapper = userSiteMapper;
    }

    @Override
    public boolean existsMembership(String userId, String siteId) {
        return userSiteMapper.exists(userId, siteId) > 0;
    }

    @Override
    public Optional<String> findOwnerUserId(String siteId) {
        return Optional.ofNullable(userSiteMapper.findOwnerUserId(siteId));
    }
}
