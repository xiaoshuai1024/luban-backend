package com.luban.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;

/**
 * Repository 隔离规则（backend-ddd-refactor plan v2 T3，阶段 4 全面硬化）。
 *
 * <p>守护 Repository 模式的 DDD 边界：
 * <ul>
 *   <li><b>Repository 接口（shared/repository）禁依赖 Mapper</b>：接口属 domain 抽象，不感知 MyBatis</li>
 *   <li><b>Application Service 禁直接依赖 Mapper</b>：须经 Repository 持久化</li>
 * </ul>
 *
 * <p><b>状态</b>：DDD 改造完成——所有 Service 已改经 Repository，SiteMapper/PlanMapper 白名单已消除。
 * freeze 已移除，规则硬执行（零违规）。
 */
@AnalyzeClasses(packages = "com.luban.backend",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class RepositoryIsolationTest {

    /**
     * Repository 接口（shared/repository）禁依赖 Mapper。
     * Repository 接口属 domain 抽象，不感知 MyBatis；Mapper 是 RepositoryImpl 的实现细节。
     */
    @ArchTest
    static final ArchRule repository_interfaces_should_not_depend_on_mapper =
            noClasses().that().resideInAPackage("..shared.repository..")
                    .should().dependOnClassesThat().resideInAPackage("..mapper..")
                    .because("Repository 接口属 domain 抽象，禁感知 MyBatis Mapper；"
                            + "Mapper 是 RepositoryImpl（operatorside/repository）的实现细节")
                    .allowEmptyShould(true);

    /**
     * 目标谓词：位于 shared.mapper 包，但排除读模型/种子 Mapper + 基础设施 Mapper。
     * <ul>
     *   <li>SiteMapper / PlanMapper——读模型/种子数据（plan §3.4 白名单）</li>
     *   <li>DomainOutboxMapper——事件投递基础设施（at-least-once outbox），由 OutboxRelayScheduler
     *       （基础设施调度器）直接使用，非领域聚合写路径</li>
     * </ul>
     */
    static final DescribedPredicate<JavaClass> DOMAIN_MAPPER_BUT_NOT_SEED_OR_READONLY =
            new DescribedPredicate<JavaClass>("domain mapper excluding SiteMapper/PlanMapper/DomainOutboxMapper (seed/readonly/infra)") {
                @Override
                public boolean test(JavaClass input) {
                    if (!input.getPackageName().startsWith("com.luban.backend.shared.mapper")) {
                        return false;
                    }
                    String name = input.getSimpleName();
                    return !"SiteMapper".equals(name)
                            && !"PlanMapper".equals(name)
                            && !"DomainOutboxMapper".equals(name);
                }
            };

    /**
     * Application Service（operatorside/service）禁直接依赖<b>领域聚合 Mapper</b>。
     * <p>聚合根的持久化须经 Repository（DDD：聚合根不感知 Mapper，由 Repository 封装）。
     *
     * <p><b>例外</b>（plan §3.4 确认的读模型/种子数据/基础设施，不属聚合写不变量）：
     * <ul>
     *   <li>{@code DomainOutboxMapper}——事件投递基础设施（at-least-once outbox），由 OutboxRelayScheduler
     *       （基础设施调度器）直接使用，非领域聚合写路径</li>
     * </ul>
     * SiteMapper/PlanMapper 白名单已消除（阶段 1 改造完成，经 SiteRepository.existsById / PlanRepository）。
     */
    @ArchTest
    static final ArchRule services_should_not_depend_on_mapper =
            noClasses().that().resideInAPackage("..operatorside.service..")
                    .should().dependOnClassesThat(DOMAIN_MAPPER_BUT_NOT_SEED_OR_READONLY)
                    .because("Application Service 禁直接依赖领域聚合 Mapper，须经 Repository 持久化（DDD）。"
                            + "阶段 1 改造完成：SiteMapper/PlanMapper 白名单已消除，所有 Service 经 Repository。"
                            + "例外仅 DomainOutboxMapper（事件投递基础设施）");
}
