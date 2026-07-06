package com.luban.backend.shared.port;

import java.util.Optional;

/**
 * 站点成员关系 Port（跨模块依赖倒置）。
 *
 * <p>封装 {@code user_sites} 授权映射表（非聚合子实体，无对应聚合根）。
 * 被多处复用：{@code TenantGuardService}（鉴权）、{@code LeadService}（留资归属）、
 * {@code FeatureGateService}（放行判定）。Port 接口置于 shared，实现置于 operatorside，
 * 消除 Service 对 {@code UserSiteMapper} 的直连。
 */
public interface SiteMembershipPort {

    /** 用户是否有权访问站点。 */
    boolean existsMembership(String userId, String siteId);

    /** 站点所有者 user_id（role=owner/admin，取最早一条）；无则 empty。 */
    Optional<String> findOwnerUserId(String siteId);
}
