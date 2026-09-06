package com.ray.service;

import com.ray.dto.MerchantVoucherProductCreateDTO;
import com.ray.dto.MerchantVoucherProductOffSaleDTO;
import com.ray.dto.MerchantVoucherProductSubmitDTO;
import com.ray.dto.MerchantVoucherProductUpdateDTO;
import com.ray.result.PageResult;
import com.ray.vo.MerchantVoucherProductVO;

/** 提供当前门店四类团购券的草稿、复制、删除与提交能力。 */
public interface MerchantVoucherProductService {
    /** 按审核状态、券型和关键词分页查询当前门店商品。 */
    PageResult<MerchantVoucherProductVO> list(
            String reviewStatus, String productType, String keyword, int page, int size);

    /** 创建券型不可变的空草稿。 */
    MerchantVoucherProductVO create(MerchantVoucherProductCreateDTO request);

    /** 查询当前门店商品完整草稿或只读事实。 */
    MerchantVoucherProductVO get(Long productId);

    /** 按乐观锁版本保存完整草稿快照。 */
    MerchantVoucherProductVO update(Long productId, MerchantVoucherProductUpdateDTO request);

    /** 删除从未提交且没有订单的草稿。 */
    void delete(Long productId);

    /** 将本门店任意状态商品复制为独立草稿。 */
    MerchantVoucherProductVO copy(Long productId);

    /** 校验完整性并幂等提交审核。 */
    MerchantVoucherProductVO submit(
            Long productId, String idempotencyKey, MerchantVoucherProductSubmitDTO request);

    MerchantVoucherProductVO offSale(
            Long productId, String idempotencyKey, MerchantVoucherProductOffSaleDTO request);
}
