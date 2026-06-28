package com.luban.backend.shared.port;

import com.luban.backend.shared.dto.LeadSubmitRequest;
import com.luban.backend.shared.dto.LeadSubmitResult;

/**
 * 留资提交端口（app-deeplink-backend-arch plan T2）。
 *
 * <p>定义 C 端（publicside）需要的留资提交能力契约。
 * 由 operatorside 的 {@code LeadService} 实现，publicside 的
 * {@code PublicLeadController} 仅依赖此接口，不直接依赖 operatorside 类。
 *
 * <p>依赖倒置：把"publicside 需要的能力"抽象为 shared 端口，
 * 消除 publicside → operatorside 的直接依赖（plan §6.1 隔离）。
 */
public interface LeadSubmissionPort {
    /**
     * 处理访客留资提交（去重/加密/入库/通知）。
     *
     * @param req 留资请求（含 formId、contact 等）
     * @return 提交结果（含 leadId、status、dedup 标志）
     */
    LeadSubmitResult submit(LeadSubmitRequest req);
}
