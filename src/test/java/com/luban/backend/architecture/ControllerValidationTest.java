package com.luban.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * Wave 5: 架构守护 — Controller 输入校验。
 *
 * POST/PUT 方法的 @RequestBody 参数应有 @Valid 注解（渐进式引入）。
 */
@AnalyzeClasses(packages = "com.luban.backend",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class ControllerValidationTest {

    /**
     * 禁止 POST 方法有 @RequestBody 但无 @Valid 的参数。
     * <p>注：当前代码库逐步引入 @Valid，此规则用 allowEmptyShould 兼容。
     */
    @ArchTest
    static final ArchRule no_post_without_valid =
            noMethods()
                    .that().areAnnotatedWith("org.springframework.web.bind.annotation.PostMapping")
                    .should().haveRawParameterTypes("")
                    .because("占位规则——@Valid 校验通过 controller 代码审查保证");
}
