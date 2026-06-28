/**
 * 共享模块：纯领域层 + 基础设施，被 publicside 与 operatorside 共同依赖。
 *
 * <p>负责：
 * <ul>
 *   <li>{@code entity} — 领域实体（贫血 POJO，被聚合根封装行为）</li>
 *   <li>{@code domain} — 聚合根（Site/Campaign/Channel，封装 invariant）</li>
 *   <li>{@code mapper} — 共享只读 mapper（如 SiteMapper、PublishedPageMapper）</li>
 *   <li>{@code crypto} — 加密原语（如 LeadCryptoService，publicside 经此访问，不依赖 operatorside）</li>
 *   <li>{@code auth}、{@code config}、{@code exception} — 两层共享的基础设施</li>
 * </ul>
 *
 * <p><b>依赖方向（ArchUnit 守护）：</b>
 * <ul>
 *   <li>❌ 禁止依赖 {@code publicside} 或 {@code operatorside}（纯领域层，不反向依赖调用方）</li>
 *   <li>✅ 仅依赖自身 + 标准库 + 框架基础（Spring/MyBatis）</li>
 * </ul>
 *
 * <p>归属：app-deeplink-backend-arch plan T1。
 */
package com.luban.backend.shared;
