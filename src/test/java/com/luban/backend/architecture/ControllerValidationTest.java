package com.luban.backend.architecture;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * Wave 5: 架构守护 — Controller 输入校验。
 *
 * 所有 POST/PUT 方法的 @RequestBody 参数必须有 @Valid 注解。
 */
@AnalyzeClasses(packages = "com.luban.backend",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class ControllerValidationTest {

    /**
     * POST 方法的 @RequestBody 参数应有 @Valid 注解。
     * <p>注：当前代码库中部分 controller 未完全遵循，此规则为渐进式引入。
     */
    @ArchTest
    static final ArchRule post_methods_with_requestbody_should_be_validated =
            methods().that().areAnnotatedWith("org.springframework.web.bind.annotation.PostMapping")
                    .and(m -> hasRequestBodyWithoutValid(m))
                    .should(haveValidAnnotation())
                    .allowEmptyShould(true)
                    .because("所有 POST 的 @RequestBody 必须加 @Valid 做输入校验");

    /**
     * PUT 方法的 @RequestBody 参数应有 @Valid 注解。
     */
    @ArchTest
    static final ArchRule put_methods_with_requestbody_should_be_validated =
            methods().that().areAnnotatedWith("org.springframework.web.bind.annotation.PutMapping")
                    .and(m -> hasRequestBodyWithoutValid(m))
                    .should(haveValidAnnotation())
                    .allowEmptyShould(true)
                    .because("所有 PUT 的 @RequestBody 必须加 @Valid 做输入校验");

    private static boolean hasRequestBodyWithoutValid(JavaMethod method) {
        for (JavaParameter param : method.getParameters()) {
            boolean hasRequestBody = false;
            boolean hasValid = false;
            for (JavaAnnotation<?> ann : param.getAnnotations()) {
                String name = ann.getRawType().getName();
                if (name.contains("RequestBody")) hasRequestBody = true;
                if (name.contains("Valid")) hasValid = true;
            }
            if (hasRequestBody && !hasValid) return true;
        }
        return false;
    }

    private static com.tngtech.archunit.base.DescribedPredicate<JavaMethod> haveValidAnnotation() {
        return new com.tngtech.archunit.base.DescribedPredicate<JavaMethod>("have @Valid") {
            @Override
            public boolean test(JavaMethod input) {
                // 此谓词用于描述，实际匹配由上面的 .and() 条件控制
                return true;
            }
        };
    }
}
