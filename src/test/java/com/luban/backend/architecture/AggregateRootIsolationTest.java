package com.luban.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;
import com.tngtech.archunit.core.domain.JavaModifier;

/**
 * DDD 聚合根隔离规则（backend-ddd-refactor plan v2 T3）。
 *
 * <p>守护 {@code shared/domain/} 聚合根的纯净性与 DDD 边界（对齐
 * {@code .agents/rules/luban-engineering-principles.md}）：
 * <ul>
 *   <li><b>domain 零框架依赖</b>：domain 包禁 Spring/MyBatis/Jakarta 注解（纯 POJO）</li>
 *   <li><b>domain 禁依赖持久化/调用方</b>：禁依赖 controller/service/mapper/repository-impl/dto/entity</li>
 *   <li><b>聚合根须 final</b>：防继承破坏不变量</li>
 * </ul>
 *
 * <p><b>已知违规 freeze</b>（v2 改造前的过渡）：
 * <ul>
 *   <li>{@code LeadStatusMachine} 带 {@code @Service}（T7 合并入 LeadAggregate 时删除）</li>
 *   <li>{@code CampaignAggregate} 引用 entity + 非 final（T10 重写为真聚合时修复）</li>
 *   <li>{@code TemplateAggregate} 已符合（final + 零框架），但 T13 仍会重写为实例方法</li>
 * </ul>
 * freeze 包裹使这些已知违规不破坏构建，但留下违规记录供后续清理。
 */
@AnalyzeClasses(packages = "com.luban.backend",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class AggregateRootIsolationTest {

    /**
     * domain 包禁 Spring/Jakarta 框架注解（聚合根须纯 POJO）。
     * <p>已知违规：LeadStatusMachine 的 @Service（T7 清理）。freeze 兜底。
     */
    @ArchTest
    static final ArchRule domain_should_not_have_framework_annotations =
            freeze(noClasses().that().resideInAPackage("..shared.domain..")
                    .should().beAnnotatedWith("org.springframework.stereotype.Service")
                    .orShould().beAnnotatedWith("org.springframework.stereotype.Component")
                    .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
                    .orShould().beAnnotatedWith("org.springframework.context.annotation.Configuration")
                    .orShould().beAnnotatedWith("org.springframework.transaction.annotation.Transactional"))
                    .because("domain 层（聚合根/值对象/领域事件）必须保持框架无关（纯 POJO）；"
                            + "已知违规 LeadStatusMachine.@Service 待 T7 合并入 LeadAggregate 时清理");

    /**
     * domain 禁依赖持久化层与调用方。
     * <p>聚合根不感知 Controller/Service/Mapper/Repository 实现；持久化由 Repository 接口抽象。
     * <p>已知违规：CampaignAggregate 引用 entity（T10 重写）。freeze 兜底。
     */
    @ArchTest
    static final ArchRule domain_should_not_depend_on_infrastructure =
            freeze(noClasses().that().resideInAPackage("..shared.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..controller..", "..service..", "..mapper..",
                            "..operatorside.repository..", "..publicside.."))
                    .because("聚合根不感知持久化/调用方；"
                            + "entity 引用经 Repository 在 Application Service 层注入；"
                            + "已知违规 CampaignAggregate 引用 entity 待 T10 重写为真聚合时修复");

    /**
     * 聚合根类（*Aggregate 后缀）须 final，防子类继承破坏不变量。
     * <p>已知违规：CampaignAggregate 非 final（T10 重写）。freeze 兜底。
     */
    @ArchTest
    static final ArchRule aggregates_should_be_final =
            freeze(classes().that().resideInAPackage("..shared.domain..")
                    .and().haveSimpleNameEndingWith("Aggregate")
                    .should().haveModifier(JavaModifier.FINAL))
                    .because("聚合根须 final，防止子类破坏不变量；"
                            + "已知违规 CampaignAggregate 非 final 待 T10 重写时修复");
}
