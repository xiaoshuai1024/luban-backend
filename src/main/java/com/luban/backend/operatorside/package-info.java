/**
 * 运营端模块：处理需要鉴权（JWT + TenantGuard）的管理请求。
 *
 * <p>负责：
 * <ul>
 *   <li>{@code /sites}、{@code /sites/{id}/pages} — 站点/页面管理</li>
 *   <li>{@code /leads}、{@code /users}、{@code /forms} — 线索/用户/表单管理</li>
 *   <li>{@code /channels}、{@code /campaigns} — 渠道/活动管理（app-deeplink-backend-arch plan）</li>
 *   <li>{@code /datasources}、{@code /collections} — 数据源/内容集合管理</li>
 *   <li>{@code /analytics}、{@code /billing}、{@code /ab/experiments} — 运营报表/计费/AB 管理</li>
 * </ul>
 *
 * <p><b>依赖方向（ArchUnit 守护）：</b>
 * <ul>
 *   <li>✅ 可依赖 {@code shared}（聚合根、实体、共享基础设施）</li>
 *   <li>❌ 禁止依赖 {@code publicside}（C 端模块）</li>
 * </ul>
 *
 * <p>归属：app-deeplink-backend-arch plan T1。
 */
package com.luban.backend.operatorside;
