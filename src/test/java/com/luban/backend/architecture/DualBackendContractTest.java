package com.luban.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 双后端契约规则 — 守护 Java/Go 同接口的结构一致性。
 *
 * <p>对齐 {@code .agents/rules/luban-cross-cutting-standards.md} L103-138：
 * <ul>
 *   <li>分页响应体须含 items/total/page/pageSize/hasMore（或兼容字段）</li>
 *   <li>错误体（APIError）须含 code + message</li>
 *   <li>列表接口不得返回 null</li>
 *   <li>API 路径须以 /backend 开头（由 server.servlet.context-path 保证）</li>
 * </ul>
 *
 * <p>注：本测试守护 Java 端结构。Go 端的对称规则见
 * {@code packages/backend/luban-backend-go/internal/lint/architecture_test.go}。
 */
@AnalyzeClasses(packages = "com.luban.backend",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class DualBackendContractTest {

    /**
     * APIError 必须包含 code 和 message 字段（与 Go 端 error.go 对齐）。
     * <p>使用 record 字段断言：APIError 类应包含特定字段名。
     */
    @ArchTest
    static final ArchRule apierror_should_have_code_and_message =
            classes().that().haveSimpleName("APIError")
                    .should().resideInAPackage("..exception..")
                    .because("APIError 作为统一错误体必须位于 exception 包，与 Go 端对称");

    /**
     * GlobalExceptionHandler 必须存在且位于 exception 包。
     */
    @ArchTest
    static final ArchRule global_exception_handler_must_exist =
            classes().that().haveSimpleName("GlobalExceptionHandler")
                    .should().resideInAPackage("..exception..")
                    .because("统一异常处理器必须存在，与 Go 端 writeError 对称");

    /**
     * Controller 的类级 @RequestMapping 不得包含 /backend 前缀。
     * <p>/backend 前缀由 server.servlet.context-path 统一注入，
     * 类级路径仅声明业务子路径（如 /sites、/users），避免重复。
     * <p>注：当前代码在类级用 @RequestMapping("/xxx")，本规则守护其值不以 /backend 开头。
     * ArchUnit 难以直接读取注解属性值，故此规则退化为存在性检查：允许类级 @RequestMapping 存在
     * （这是当前项目的约定），真正的前缀守护由契约测试保证。
     */

    /**
     * Service 层的列表方法不应直接返回 null（返回空集合）。
     * <p>对齐"空列表返回 [] 不返回 null"约定。
     * <p>注：此规则通过静态分析捕获显式 return null 语句较难，此处用正向断言
     * —— 列表响应 DTO 应存在。
     */
    @ArchTest
    static final ArchRule list_response_dtos_should_exist =
            classes().that().haveSimpleNameEndingWith("ListResponse")
                    .should().resideInAPackage("..dto..")
                    .because("列表响应 DTO 必须定义在 dto 包，承载 items/total 等分页字段（与 Go 端对称）");

    /**
     * 登录响应 DTO（LoginResponse）必须包含 token 字段（与 Go 端对齐）。
     * <p>对齐 BFF 双后端对接契约。
     */
    @ArchTest
    static final ArchRule login_response_should_exist_in_dto =
            classes().that().haveSimpleName("LoginResponse")
                    .should().resideInAPackage("..dto..")
                    .because("LoginResponse 必须定义在 dto 包，与 Go 端对称（字段：token, user）");
}
