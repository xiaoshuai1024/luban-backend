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
 * <p><b>T2 已完成</b>：publicside 原依赖 operatorside 的 3 处耦合（留资提交/集合公开读/埋点接收）
 * 已通过 shared/port 端口接口（依赖倒置）消除，publicside 现仅依赖 shared port 接口。
 * 规则当前零违规；freeze 保留作为防御机制，应对未来可能的临时引入。
 */
@AnalyzeClasses(packages = "com.luban.backend",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class PublicsideIsolationTest {

    /**
     * publicside 禁止依赖 operatorside。
     * 这是 C 端与运营端的安全边界。T2 已通过 shared/port 端口接口消除全部历史耦合，
     * 当前零违规。freeze 保留作为防御机制，防止未来回退。
     */
    @ArchTest
    static final ArchRule publicside_should_not_depend_on_operatorside =
            freeze(noClasses().that().resideInAPackage("..publicside..")
                    .should().dependOnClassesThat().resideInAPackage("..operatorside.."))
                    .because("C 端（publicside）禁止依赖运营端（operatorside）；"
                            + "C 端所需运营域能力经 shared/port 端口接口注入（T2 已完成）");

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
