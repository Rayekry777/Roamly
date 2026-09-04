package com.ray.service;
import com.ray.vo.VoucherQrTokenVO;
public interface VoucherQrTokenService { VoucherQrTokenVO issue(Long voucherId); String consume(String token); }
