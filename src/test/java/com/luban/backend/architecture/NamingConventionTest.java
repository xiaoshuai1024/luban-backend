package com.luban.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * 命名规范规则 — 强制各层类按约定命名。
 *
 * <p>对齐 {@code docs/dev/alibaba-java-development-manual.md} 与项目既有风格：
 * <ul>
 *   <li>Controller 类名以 Controller 结尾</li>
 *   <li>Service 类名以 Service 结尾</li>
 *   <li>Mapper 类名以 Mapper 结尾</li>
 *   <li>DTO 包仅含 records（请求/响应模型）</li>
 * </ul>
 */
@AnalyzeClasses(packages = "com.luban.backend",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class NamingConventionTest {

    /**
     * controller 包下所有类必须以 Controller 结尾。
     */
    @ArchTest
    static final ArchRule controllers_should_be_named_Controller =
            classes().that().resideInAPackage("..controller..")
                    .should().haveSimpleNameEndingWith("Controller")
                    .because("Controller 类必须以 'Controller' 结尾，便于识别与路由扫描");

    /**
     * service 包下所有类必须以 Service 结尾。
     */
    @ArchTest
    static final ArchRule services_should_be_named_Service =
            classes().that().resideInAPackage("..service..")
                    .should().haveSimpleNameEndingWith("Service")
                    .because("Service 类必须以 'Service' 结尾，符合分层命名约定");

    /**
     * mapper 包下所有类必须以 Mapper 结尾。
     */
    @ArchTest
    static final ArchRule mappers_should_be_named_Mapper =
            classes().that().resideInAPackage("..mapper..")
                    .should().haveSimpleNameEndingWith("Mapper")
                    .because("Mapper 类必须以 'Mapper' 结尾，符合 MyBatis 接口约定");

    /**
     * controller 包下的类必须以 @RestController 注解标记。
     */
    @ArchTest
    static final ArchRule controllers_should_be_annotated_with_RestController =
            classes().that().resideInAPackage("..controller..")
                    .should().beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                    .because("Controller 必须使用 @RestController 暴露 REST API");

    /**
     * mapper 包下的接口必须以 @Mapper 注解标记（MyBatis）。
     */
    @ArchTest
    static final ArchRule mappers_should_be_annotated_with_Mapper =
            classes().that().resideInAPackage("..mapper..")
                    .should().beAnnotatedWith("org.apache.ibatis.annotations.Mapper")
                    .because("Mapper 必须使用 @Mapper 注解以便 MyBatis 扫描注册");
}
