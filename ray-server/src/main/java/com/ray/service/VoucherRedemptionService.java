package com.ray.service;
import com.ray.dto.*;
import com.ray.result.PageResult;
import com.ray.vo.*;
import java.time.LocalDateTime;

/** 商户核销预览、确认、查询、备注和撤销能力。 */
public interface VoucherRedemptionService {
    /** 通过手输券码创建只读核销预览。 */
    VoucherRedemptionPreviewVO preview(VoucherRedemptionPreviewDTO request);
    /** 通过固定二维码创建只读核销预览。 */
    VoucherRedemptionPreviewVO previewByQrToken(String token);
    /** 幂等确认一次核销。 */
    VoucherRedemptionVO confirm(VoucherRedemptionConfirmDTO request, String key);
    /** 撤销尚未结算的核销。 */
    VoucherRedemptionVO reverse(Long id, VoucherRedemptionReversalDTO request, String key);
    /** 保留管理端和旧调用方基础分页查询。 */
    PageResult<VoucherRedemptionVO> list(int page, int size, boolean admin);
    /** 按状态、关键词和核销时间查询当前门店核销。 */
    PageResult<VoucherRedemptionVO> listMerchant(
            String status, String keyword, LocalDateTime from, LocalDateTime to, int page, int size);
    /** 查询核销详情。 */
    VoucherRedemptionVO get(Long id, boolean admin);
    /** 更新当前门店核销记录的商家备注。 */
    VoucherRedemptionVO updateNote(Long id, RedemptionNoteUpdateDTO request);
    /** 查询核销时冻结的收入明细。 */
    RedemptionIncomeBreakdownVO income(Long id);
}
