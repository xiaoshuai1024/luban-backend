package com.luban.backend.architecture;

import com.luban.backend.shared.exception.BusinessException;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 异常层次规则 — 强制所有自定义异常继承 BusinessException，由 GlobalExceptionHandler 统一处理。
 *
 * <p>对齐 {@code .agents/rules/luban-cross-cutting-standards.md} 错误体约定：
 * <ul>
 *   <li>所有自定义异常必须继承 BusinessException</li>
 *   <li>禁止直接抛出裸 RuntimeException / IllegalArgumentException</li>
 *   <li>GlobalExceptionHandler 必须处理 BusinessException 并返回 APIError</li>
 * </ul>
 */
@AnalyzeClasses(packages = "com.luban.backend",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class ExceptionHierarchyTest {

    /**
     * exception 包下所有自定义异常必须继承 BusinessException。
     */
    @ArchTest
    static final ArchRule custom_exceptions_should_extend_BusinessException =
            classes().that().resideInAPackage("..exception..")
                    .and().areAssignableTo(RuntimeException.class)
                    .should().beAssignableTo(BusinessException.class)
                    .because("所有自定义异常必须继承 BusinessException，确保 GlobalExceptionHandler 能统一捕获");

    /**
     * Controller 不应直接抛出裸 RuntimeException。
     * <p>（业务异常应通过 BusinessException 子类表达，由全局处理器转换）
     */
    @ArchTest
    static final ArchRule controllers_should_not_throw_bare_RuntimeException =
            noClasses().that().resideInAPackage("..controller..")
                    .should().callConstructor(RuntimeException.class)
                    .because("Controller 禁止抛裸 RuntimeException，应使用 BusinessException 子类");

    /**
     * exception 包下的异常类必须位于 com.luban.backend.exception 包内。
     * <p>当前架构采用 BusinessException + 静态工厂方法模式（无子类继承），
     * 故此规则守护 BusinessException 基类自身的位置。
     */
    @ArchTest
    static final ArchRule business_exception_should_reside_in_exception_package =
            classes().that().haveSimpleName("BusinessException")
                    .should().resideInAPackage("..exception..")
                    .because("BusinessException 作为统一业务异常基类必须位于 exception 包");

    /**
     * GlobalExceptionHandler 必须使用 @RestControllerAdvice 注解。
     */
    @ArchTest
    static final ArchRule global_exception_handler_should_be_advice =
            classes().that().haveSimpleName("GlobalExceptionHandler")
                    .should().beAnnotatedWith("org.springframework.web.bind.annotation.RestControllerAdvice")
                    .because("全局异常处理器必须以 @RestControllerAdvice 注册，才能拦截所有 Controller 异常");
}
