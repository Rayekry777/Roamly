package com.ray.service;
import com.ray.vo.AdminEventTicketVO;
public interface EventTicketService { AdminEventTicketVO issue(); boolean valid(String ticket); }
