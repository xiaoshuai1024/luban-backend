package com.luban.backend.shared.support;

import com.luban.backend.shared.entity.Form;
import com.luban.backend.shared.entity.Lead;

/**
 * 新线索通知（P0：Webhook 单向通知；实现可异步）。
 */
public interface LeadNotifyService {

    /** 新线索入库后触发。 */
    void notifyNewLead(Lead lead, Form form);
}
