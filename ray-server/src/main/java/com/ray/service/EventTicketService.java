package com.ray.service;
import com.ray.vo.AdminEventTicketVO;
public interface EventTicketService {
    AdminEventTicketVO issue();

    /** 原子消费绑定当前管理员的短期票据。 */
    boolean consume(String ticket, Long adminId);
}
