package com.ray.service;

import com.ray.dto.VoucherOrderCreateDTO;
import com.ray.vo.UserVoucherVO;
import com.ray.vo.VoucherOrderDetailVO;
import com.ray.vo.VoucherOrderConfirmationVO;
import com.ray.vo.VoucherOrderVO;
import com.ray.vo.VoucherProductVO;
import java.util.List;
import com.ray.result.PageResult;

/** 提供团购下单、订单和用户券查询能力。 */
public interface VoucherTradeService {
    /** 读取服务端权威商品价格、数量边界与订单金额，不占用库存。 */
    VoucherOrderConfirmationVO confirmOrder(Long productId, Integer quantity);

    /** 创建待支付订单并预扣商品库存。 */
    VoucherOrderVO createOrder(Long productId, VoucherOrderCreateDTO request, String idempotencyKey);

    /** 查询当前用户订单分页。 */
    PageResult<VoucherOrderVO> listOrders(String status, int page, int size);

    /** 查询当前用户订单详情。 */
    VoucherOrderDetailVO getOrder(Long orderId);

    /** 取消当前用户未支付订单并返还库存。 */
    void cancelOrder(Long orderId);

    /** 查询当前用户券包。 */
    PageResult<UserVoucherVO> listVouchers(String status, int page, int size);

    /** 查询当前用户券详情。 */
    UserVoucherVO getVoucher(Long userVoucherId);
}
