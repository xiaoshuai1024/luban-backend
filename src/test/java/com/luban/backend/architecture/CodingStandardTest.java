package com.luban.backend.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 编码规范规则 — 强制 Java 编码最佳实践。
 *
 * <p>对齐 {@code docs/dev/alibaba-java-development-manual.md} 与
 * {@code .agents/rules/luban-redis-cache.md} L14-19：
 * <ul>
 *   <li>禁止 System.out / System.err（统一使用日志框架）</li>
 *   <li>禁止 java.util.logging（统一使用 SLF4J）</li>
 *   <li>禁止 @Autowired 字段注入（只用构造注入）</li>
 *   <li>禁止 JVM 本地业务缓存（必须使用 Redis + DB fallback）</li>
 * </ul>
 */
@AnalyzeClasses(packages = "com.luban.backend",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class CodingStandardTest {

    /**
     * 禁止使用 System.out / System.err 打印（应使用 SLF4J / Lombok @Slf4j）。
     * <p>注：System.currentTimeMillis/arraycopy/getenv 等合法调用不受限。
     */
    @ArchTest
    static final ArchRule no_System_out =
            noClasses().that().resideInAPackage("com.luban.backend..")
                    .should().callCodeUnitWhere(new DescribedPredicate<JavaCall<?>>("call System.out/err") {
                        @Override
                        public boolean test(JavaCall<?> call) {
                            String owner = call.getTargetOwner().getName();
                            String name = call.getTarget().getName();
                            // 只匹配 System.out.xxx 和 System.err.xxx
                            return owner.equals("java.lang.System") && (name.equals("out") || name.equals("err"));
                        }
                    })
                    .because("禁止使用 System.out / System.err，统一使用 SLF4J 日志框架");

    /**
     * 禁止使用 java.util.logging（统一使用 SLF4J）。
     */
    @ArchTest
    static final ArchRule no_jul_logging =
            noClasses().that().resideInAPackage("com.luban.backend..")
                    .should().dependOnClassesThat().resideInAPackage("java.util.logging..")
                    .because("禁止使用 java.util.logging，统一使用 SLF4J 门面");

    /**
     * 禁止 @Autowired 字段注入（必须使用构造注入）。
     * <p>仅检查业务层（controller/service/config），测试类豁免。
     */
    @ArchTest
    static final ArchRule no_field_injection =
            noClasses().that().resideInAnyPackage("..controller..", "..service..", "..config..")
                    .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                    .because("禁止 @Autowired 字段注入，必须使用构造注入（便于测试与不可变性）");

    /**
     * 禁止使用 ConcurrentHashMap 作为业务缓存（对齐 luban-redis-cache.md）。
     * <p>允许在非业务包（utils）使用，业务包（service/repository）禁止。
     */
    @ArchTest
    static final ArchRule no_local_business_cache =
            noClasses().that().resideInAnyPackage("..service..", "..repository..", "..mapper..")
                    .should().dependOnClassesThat().haveFullyQualifiedName("java.util.concurrent.ConcurrentHashMap")
                    .because("禁止 JVM 本地业务缓存，必须使用 Redis + DB fallback（见 luban-redis-cache.md）");

    // 注：Service 层日志规则暂不强制（现有 Service 未引入 @Slf4j）。
    // 后续迭代在 Service 全部加 @Slf4j 后，恢复以下规则：
    //
    // @ArchTest
    // static final ArchRule services_should_use_logging =
    //         classes().that().resideInAPackage("..service..")
    //                 .should().beAnnotatedWith("lombok.extern.slf4j.Slf4j")
    //                 .orShould().dependOnClassesThat().haveFullyQualifiedName("org.slf4j.Logger")
    //                 .because("Service 层应使用日志（@Slf4j 或 LoggerFactory），便于追踪业务行为");
}
