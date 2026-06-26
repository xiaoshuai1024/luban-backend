package com.luban.backend.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * 命名规范规则 — 强制各层类按约定命名。
 *
 * <p>排除内部类（controller/service 内嵌 record DTO 是既定模式）。
 */
@AnalyzeClasses(packages = "com.luban.backend",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class NamingConventionTest {

    /** 排除内部类（类名含 $） */
    private static final DescribedPredicate<JavaClass> NOT_NESTED =
            new DescribedPredicate<JavaClass>("not nested") {
                @Override
                public boolean test(JavaClass input) {
                    return !input.getName().contains("$");
                }
            };

    /** 仅检查类名含 "Service" 的类（豁免 StatusMachine/Scheduler 等工具类） */
    private static final DescribedPredicate<JavaClass> NAME_CONTAINS_SERVICE =
            new DescribedPredicate<JavaClass>("name contains Service") {
                @Override
                public boolean test(JavaClass input) {
                    return input.getSimpleName().contains("Service");
                }
            };

    @ArchTest
    static final ArchRule controllers_should_be_named_Controller =
            classes().that().resideInAPackage("..controller..")
                    .and(NOT_NESTED)
                    .should().haveSimpleNameEndingWith("Controller")
                    .because("Controller 类必须以 'Controller' 结尾");

    @ArchTest
    static final ArchRule services_should_be_named_Service =
            classes().that().resideInAPackage("..service..")
                    .and(NOT_NESTED)
                    .and(NAME_CONTAINS_SERVICE)
                    .should().haveSimpleNameEndingWith("Service")
                    .because("Service 类必须以 'Service' 结尾（工具类如 StatusMachine/Scheduler 豁免）");

    @ArchTest
    static final ArchRule mappers_should_be_named_Mapper =
            classes().that().resideInAPackage("..mapper..")
                    .and(NOT_NESTED)
                    .should().haveSimpleNameEndingWith("Mapper")
                    .because("Mapper 类必须以 'Mapper' 结尾");

    @ArchTest
    static final ArchRule controllers_should_be_annotated_with_RestController =
            classes().that().resideInAPackage("..controller..")
                    .and(NOT_NESTED)
                    .should().beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                    .because("Controller 必须使用 @RestController 暴露 REST API");

    @ArchTest
    static final ArchRule mappers_should_be_annotated_with_Mapper =
            classes().that().resideInAPackage("..mapper..")
                    .and(NOT_NESTED)
                    .should().beAnnotatedWith("org.apache.ibatis.annotations.Mapper")
                    .because("Mapper 必须使用 @Mapper 注解以便 MyBatis 扫描注册");
}
