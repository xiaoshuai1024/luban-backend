/**
 * C 端（访客）模块：处理公开免鉴权的请求路径。
 *
 * <p>负责：
 * <ul>
 *   <li>{@code /public/**} — 公开页面/配置/集合读取（SSR 数据源）</li>
 *   <li>{@code /lead/forms/{id}/submit} — 公开留资提交</li>
 *   <li>{@code /public/short/{shortCode}} — 短链解析（app-deeplink-backend-arch plan）</li>
 *   <li>{@code /public/analytics/**} — 访客埋点接收</li>
 *   <li>{@code /public/ab/assign}、{@code /public/feature-gates} — 访客侧 AB/特性门禁读</li>
 * </ul>
 *
 * <p><b>依赖方向（ArchUnit 守护）：</b>
 * <ul>
 *   <li>✅ 可依赖 {@code shared}（聚合根、加密原语、共享只读 mapper、实体）</li>
 *   <li>❌ 禁止依赖 {@code operatorside}（运营模块）</li>
 * </ul>
 *
 * <p>归属：app-deeplink-backend-arch plan T1。
 */
package com.luban.backend.publicside;
