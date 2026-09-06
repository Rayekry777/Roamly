package com.ray.service;
import com.ray.dto.*;import com.ray.dto.*;
import com.ray.result.PageResult;
import com.ray.vo.*;
public interface VoucherRedemptionService { VoucherRedemptionPreviewVO preview(VoucherRedemptionPreviewDTO request); VoucherRedemptionPreviewVO previewByQrToken(String token); VoucherRedemptionVO confirm(VoucherRedemptionConfirmDTO request,String key); VoucherRedemptionVO reverse(Long id,VoucherRedemptionReversalDTO request,String key); PageResult<VoucherRedemptionVO> list(int page,int size,boolean admin); VoucherRedemptionVO get(Long id,boolean admin); }
