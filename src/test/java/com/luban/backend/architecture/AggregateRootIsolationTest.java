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
 *   <li><b>domain 零框架依赖</b>：domain 包禁 Spring/MyBatis/Jakarta 注解，且禁依赖
 *       {@code org.springframework..}/{@code jakarta..} 任何类型（纯 POJO）</li>
 *   <li><b>domain 禁依赖持久化/调用方</b>：禁依赖 controller/service/mapper/repository-impl/dto/entity</li>
 *   <li><b>聚合根须 final</b>：防继承破坏不变量</li>
 * </ul>
 *
 * <p>历史：v1 改造前存在 {@code LeadStatusMachine}（@Service）/静态工具类伪装聚合根（CampaignAggregate/
 * TemplateAggregate）等已知违规，已随 v2 全量改造清理（T7/T10/T13）。当前 freeze 仅用于增量收敛。
 */
@AnalyzeClasses(packages = "com.luban.backend",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class AggregateRootIsolationTest {

    /**
     * domain 包禁 Spring/Jakarta 框架注解（聚合根须纯 POJO）。
     */
    @ArchTest
    static final ArchRule domain_should_not_have_framework_annotations =
            freeze(noClasses().that().resideInAPackage("..shared.domain..")
                    .should().beAnnotatedWith("org.springframework.stereotype.Service")
                    .orShould().beAnnotatedWith("org.springframework.stereotype.Component")
                    .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
                    .orShould().beAnnotatedWith("org.springframework.context.annotation.Configuration")
                    .orShould().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
                    .because("domain 层聚合根值对象领域事件必须保持框架无关纯 POJO"));

    /**
     * domain 禁依赖 Spring / Jakarta 任何类型（不只是注解）。
     * <p>守护聚合根不通过 HttpStatus / HttpServletRequest 等类型把框架拉进领域层。
     * 历史上 BusinessException 曾带 org.springframework.http.HttpStatus，已重构为 int statusCode。
     */
    @ArchTest
    static final ArchRule domain_should_not_depend_on_spring_or_jakarta =
            freeze(noClasses().that().resideInAPackage("..shared.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..", "jakarta..")
                    .because("domain 层必须零框架依赖纯 POJO, Spring jakarta 任何类型都禁;"
                            + "错误码状态码用原始 int 或领域枚举表达, 框架映射留给基础设施层"));

    /**
     * domain 禁依赖持久化层与调用方。
     * <p>聚合根不感知 Controller/Service/Mapper/Repository 实现；持久化由 Repository 接口抽象。
     */
    @ArchTest
    static final ArchRule domain_should_not_depend_on_infrastructure =
            freeze(noClasses().that().resideInAPackage("..shared.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..controller..", "..service..", "..mapper..",
                            "..operatorside.repository..", "..publicside..")
                    .because("聚合根不感知持久化调用方; entity 引用经 Repository 在 Application Service 层注入"));

    /**
     * 聚合根类（*Aggregate 后缀）须 final，防子类继承破坏不变量。
     */
    @ArchTest
    static final ArchRule aggregates_should_be_final =
            freeze(classes().that().resideInAPackage("..shared.domain..")
                    .and().haveSimpleNameEndingWith("Aggregate")
                    .should().haveModifier(JavaModifier.FINAL)
                    .because("聚合根须 final 防止子类破坏不变量"));
}
