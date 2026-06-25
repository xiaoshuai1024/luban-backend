package com.luban.backend.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 分层依赖规则 — 强制 controller → service → mapper 单向依赖。
 *
 * <p>守护架构（对齐 {@code /e2e-archi} 命令 §1 与
 * {@code .agents/rules/luban-dual-backend-parity.md}）：
 * <ul>
 *   <li>controller 仅依赖 service（禁止直接依赖 mapper/entity）</li>
 *   <li>service 仅依赖 mapper/其他 service（禁止依赖 controller）</li>
 *   <li>mapper 仅依赖 entity（禁止反向依赖 service/controller）</li>
 *   <li>entity 不应持有 service/controller 的依赖</li>
 * </ul>
 */
@AnalyzeClasses(packages = "com.luban.backend",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class LayerDependencyTest {

    /**
     * Controller 只能依赖 service（或同级 controller / framework）。禁止直接调用 mapper。
     */
    @ArchTest
    static final ArchRule controllers_should_not_depend_on_mappers =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..mapper..")
                    .because("Controllers 必须通过 Service 访问数据层，禁止直接调用 Mapper");

    /**
     * Controller 不应直接持有 entity 类型作为字段依赖（应通过 DTO）。
     */
    @ArchTest
    static final ArchRule controllers_should_not_depend_on_entities =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..entity..")
                    .because("Controllers 必须使用 DTO，禁止直接暴露 entity 类型");

    /**
     * Service 不能依赖 controller（反向依赖）。
     */
    @ArchTest
    static final ArchRule services_should_not_depend_on_controllers =
            noClasses().that().resideInAPackage("..service..")
                    .should().dependOnClassesThat().resideInAPackage("..controller..")
                    .because("Service 层禁止反向依赖 Controller，违反分层原则");

    /**
     * Mapper 不能依赖 service（反向依赖）。
     */
    @ArchTest
    static final ArchRule mappers_should_not_depend_on_services =
            noClasses().that().resideInAPackage("..mapper..")
                    .should().dependOnClassesThat().resideInAPackage("..service..")
                    .because("Mapper 层禁止反向依赖 Service，违反分层原则");

    /**
     * Entity 不应依赖 service/controller（实体不应有业务逻辑依赖）。
     */
    @ArchTest
    static final ArchRule entities_should_not_depend_on_business_layers =
            noClasses().that().resideInAPackage("..entity..")
                    .should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..", "..mapper..")
                    .because("Entity 为纯 POJO，禁止持有业务层依赖");

    /**
     * 各层不出现循环依赖。
     */
    @ArchTest
    void slices_should_be_free_of_cycles(JavaClasses importedClasses) {
        slices().matching("com.luban.backend.(*)..")
                .should().beFreeOfCycles()
                .check(importedClasses);
    }
}
