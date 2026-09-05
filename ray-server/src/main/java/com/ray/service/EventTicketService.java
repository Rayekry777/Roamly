package com.ray.service;
import com.ray.vo.AdminEventTicketVO;
public interface EventTicketService {
    AdminEventTicketVO issue();

    /** 原子消费短期票据并返回票据绑定的管理员 ID。 */
    Long consume(String ticket);
}
