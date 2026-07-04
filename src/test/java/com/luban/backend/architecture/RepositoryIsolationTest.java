package com.luban.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;

/**
 * Repository 隔离规则（backend-ddd-refactor plan v2 T3）。
 *
 * <p>守护 Repository 模式的 DDD 边界：
 * <ul>
 *   <li><b>Repository 接口（shared/repository）禁依赖 Mapper</b>：接口属 domain 抽象，不感知 MyBatis</li>
 *   <li><b>Application Service 禁直接依赖 Mapper</b>：须经 Repository 持久化</li>
 * </ul>
 *
 * <p><b>状态</b>：当前 shared/repository 包尚不存在（T5-T15 建 11 个 Repository 时创建）。
 * 规则先以 freeze 兜底（零违规，因目标包为空），待 Repository 全部就位后规则自然生效。
 *
 * <p>Application Service 禁依赖 Mapper 的规则同样 freeze 兜底：
 * 当前所有 Service 仍直接调 Mapper（T5-T15 改造为经 Repository 后才符合）。
 */
@AnalyzeClasses(packages = "com.luban.backend",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class RepositoryIsolationTest {

    /**
     * Repository 接口（shared/repository）禁依赖 Mapper。
     * <p>shared/repository 包暂不存在，allowEmptyShould 允许空包评估；
     * T5-T15 建包后规则自然守护。
     */
    @ArchTest
    static final ArchRule repository_interfaces_should_not_depend_on_mapper =
            freeze(noClasses().that().resideInAPackage("..shared.repository..")
                    .should().dependOnClassesThat().resideInAPackage("..mapper.."))
                    .because("Repository 接口属 domain 抽象，禁感知 MyBatis Mapper；"
                            + "Mapper 是 RepositoryImpl（operatorside/repository）的实现细节")
                    .allowEmptyShould(true); // 包待 T5-T15 创建，暂允许空

    /**
     * 目标谓词：位于 shared.mapper 包，但排除读模型/种子 Mapper（SiteMapper/PlanMapper）。
     * 这些例外经 plan §3.4 确认（Site 是种子数据 SITE_NOT_FOUND 校验，Plan 是只读 seed）。
     */
    static final DescribedPredicate<JavaClass> DOMAIN_MAPPER_BUT_NOT_SEED_OR_READONLY =
            new DescribedPredicate<JavaClass>("domain mapper excluding SiteMapper/PlanMapper (seed/readonly)") {
                @Override
                public boolean test(JavaClass input) {
                    if (!input.getPackageName().startsWith("com.luban.backend.shared.mapper")) {
                        return false;
                    }
                    String name = input.getSimpleName();
                    return !"SiteMapper".equals(name) && !"PlanMapper".equals(name);
                }
            };

    /**
     * Application Service（operatorside/service）禁直接依赖<b>领域聚合 Mapper</b>。
     * <p>聚合根的持久化须经 Repository（DDD：聚合根不感知 Mapper，由 Repository 封装）。
     *
     * <p><b>例外</b>（plan §3.4 确认的读模型/种子数据，不属聚合写不变量）：
     * <ul>
     *   <li>{@code SiteMapper}——SITE_NOT_FOUND 跨聚合存在性校验（Site 是种子数据，
     *       对齐 TemplateService 现状，待 SiteRepository 成熟后再收紧）</li>
     *   <li>{@code PlanMapper}——Plan 是 seed 只读数据（{@code PlanService} 只读查询）</li>
     * </ul>
     *
     * <p>当前未改造的 Service（LeadService/FormService 等仍直接调领域 Mapper）由 freeze 兜底，
     * T5-T15 逐个改造后违规递减。
     */
    @ArchTest
    static final ArchRule services_should_not_depend_on_mapper =
            freeze(noClasses().that().resideInAPackage("..operatorside.service..")
                    .should().dependOnClassesThat(DOMAIN_MAPPER_BUT_NOT_SEED_OR_READONLY))
                    .because("Application Service 禁直接依赖领域聚合 Mapper，须经 Repository 持久化（DDD）；"
                            + "例外：SiteMapper（SITE_NOT_FOUND 种子校验）/ PlanMapper（只读种子）"
                            + "——详见 plan §3.4 读模型/种子数据白名单。"
                            + "当前未改造 Service 的领域 Mapper 依赖由 freeze 兜底，T5-T15 改造后递减");
    // allowStoreUpdate 已在 archunit.properties 全局开启（freeze.store.default.allowStoreUpdate=true），
    // DDD 改造期间违规集合递减时 store 自动更新。
}
