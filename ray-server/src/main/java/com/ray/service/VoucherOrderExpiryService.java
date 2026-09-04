package com.ray.service;

/** 扫描并关闭超过支付时限的订单。 */
public interface VoucherOrderExpiryService {
    int closeExpiredOrders();
}
