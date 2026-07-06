package com.luban.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * DTO / Entity 隔离规则 — 防止持久化注解泄漏到 DTO，业务注解污染到 Entity。
 *
 * <p>对齐 {@code .agents/rules/luban-cross-cutting-standards.md} L103-138：
 * <ul>
 *   <li>Entity 不应持有 Spring 框架注解（@Service/@RestController/@Repository 等）</li>
 *   <li>DTO 不应持有持久化注解（@Table/@Column/@Mapper 等）</li>
 *   <li>Controller 不得直接返回 entity 类型</li>
 * </ul>
 */
@AnalyzeClasses(packages = "com.luban.backend",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class DtoEntityIsolationTest {

    /**
     * Entity 包内的类禁止使用 Spring 业务注解（保持 POJO 纯粹）。
     */
    @ArchTest
    static final ArchRule entities_should_not_have_spring_stereotypes =
            noClasses().that().resideInAPackage("..entity..")
                    .should().beAnnotatedWith("org.springframework.stereotype.Service")
                    .orShould().beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                    .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
                    .orShould().beAnnotatedWith("org.springframework.stereotype.Component")
                    .because("Entity 必须是纯 POJO，禁止持有 Spring 业务注解");

    /**
     * DTO 包内的类禁止使用 MyBatis 持久化注解。
     */
    @ArchTest
    static final ArchRule dtos_should_not_have_persistence_annotations =
            noClasses().that().resideInAPackage("..dto..")
                    .should().beAnnotatedWith("org.apache.ibatis.annotations.Mapper")
                    .orShould().beAnnotatedWith("com.baomidou.mybatisplus.annotation.TableName")
                    .orShould().beAnnotatedWith("com.baomidou.mybatisplus.annotation.TableId")
                    .because("DTO 是 API 契约对象，禁止耦合持久化框架注解");

    // 阶段 4c 恢复：controller 内嵌 record DTO 已上提至 shared/dto，
    // publicside controller 的 Site entity 直连已改经 SiteConfigReadPort。规则硬执行。

    /**
     * Controller 禁止直接依赖 Entity 包（API 契约层不耦合持久化模型）。
     * 阶段 5 DTO 上提后，所有 controller 经 DTO/Response 对象，不直引 entity。
     */
    @ArchTest
    static final ArchRule controllers_should_not_reference_entities =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..shared.entity..")
                    .because("Controller 是 API 契约层，禁止直接依赖持久化 Entity；"
                            + "经 DTO/Response 对象与 Service 交互（阶段 5 DTO 上提后无违规）");

    /**
     * Mapper 不得依赖 DTO 包（DTO 不应进入持久化层）。
     */
    @ArchTest
    static final ArchRule mappers_should_not_reference_dtos =
            noClasses().that().resideInAPackage("..mapper..")
                    .should().dependOnClassesThat().resideInAPackage("..dto..")
                    .because("Mapper 应操作 Entity，禁止依赖 DTO（API 契约不入持久化层）");
}
