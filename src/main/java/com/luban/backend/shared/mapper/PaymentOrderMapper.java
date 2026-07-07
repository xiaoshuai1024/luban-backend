package com.luban.backend.shared.mapper;

import com.luban.backend.shared.entity.PaymentOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * payment_orders Mapper（P-001 计费/订阅闭环）。
 */
@Mapper
public interface PaymentOrderMapper {

    @Insert("INSERT INTO payment_orders (id, user_id, plan_code, amount, currency, channel, " +
            "status, pay_url, raw_response, created_at, paid_at) VALUES " +
            "(#{id}, #{userId}, #{planCode}, #{amount}, #{currency}, #{channel}, " +
            "#{status}, #{payUrl}, #{rawResponse}, #{createdAt}, #{paidAt})")
    void insert(PaymentOrder order);

    @Select("SELECT * FROM payment_orders WHERE id = #{id}")
    PaymentOrder getById(String id);

    @Update("UPDATE payment_orders SET status = #{status}, paid_at = #{paidAt}, " +
            "raw_response = #{rawResponse} WHERE id = #{id}")
    int updateStatus(String id, String status, java.time.Instant paidAt, String rawResponse);
}
