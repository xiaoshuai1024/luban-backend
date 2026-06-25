package com.luban.backend.service;

import com.luban.backend.entity.Form;
import com.luban.backend.entity.Lead;

/**
 * 新线索通知（P0：Webhook 单向通知；实现可异步）。
 */
public interface LeadNotifyService {

    /** 新线索入库后触发。 */
    void notifyNewLead(Lead lead, Form form);
}
