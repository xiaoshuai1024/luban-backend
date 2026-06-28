package com.luban.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

/**
 * 三层包隔离规则（app-deeplink-backend-arch plan T5）。
 *
 * <p>守护 publicside / operatorside / shared 三层依赖方向（见 plan §6.1）：
 * <ul>
 *   <li>publicside（C 端访客）禁止依赖 operatorside（运营）— 安全边界核心</li>
 *   <li>operatorside 禁止依赖 publicside — 防止运营逻辑混入 C 端</li>
 *   <li>shared（共享领域层）禁止反向依赖 publicside/operatorside — 纯领域不感知调用方</li>
 * </ul>
 *
 * <p><b>已知技术债（T2 待消除）</b>：publicside 的 3 个 controller 目前依赖 operatorside 的
 * 3 个 service 方法（留资提交/集合公开读/埋点接收），属于历史耦合。这些违规通过 freeze 冻结，
 * <b>新增</b> publicside→operatorside 依赖将立即失败。T2 拆分跨界 Service 时逐个解冻消除。
 */
@AnalyzeClasses(packages = "com.luban.backend",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class PublicsideIsolationTest {

    /**
     * publicside 禁止依赖 operatorside。
     * 这是 C 端与运营端的安全边界。当前 3 处历史违规已冻结（见类 Javadoc），
     * freeze 保证：已冻结违规不阻断构建，但任何<b>新增</b>违规立即失败。
     */
    @ArchTest
    static final ArchRule publicside_should_not_depend_on_operatorside =
            freeze(noClasses().that().resideInAPackage("..publicside..")
                    .should().dependOnClassesThat().resideInAPackage("..operatorside.."))
                    .because("C 端（publicside）禁止依赖运营端（operatorside）；"
                            + "已知 3 处历史违规已冻结，T2 拆分后消除");

    /**
     * operatorside 禁止依赖 publicside。
     */
    @ArchTest
    static final ArchRule operatorside_should_not_depend_on_publicside =
            noClasses().that().resideInAPackage("..operatorside..")
                    .should().dependOnClassesThat().resideInAPackage("..publicside..")
                    .because("运营端（operatorside）禁止依赖 C 端（publicside），避免职责混淆");

    /**
     * shared 禁止反向依赖 publicside 或 operatorside。
     * shared 是纯领域层，不应感知调用方。
     */
    @ArchTest
    static final ArchRule shared_should_not_depend_on_callers =
            noClasses().that().resideInAPackage("..shared..")
                    .should().dependOnClassesThat().resideInAnyPackage("..publicside..", "..operatorside..")
                    .because("shared 共享层禁止反向依赖调用方（publicside/operatorside），保持纯领域");
}
