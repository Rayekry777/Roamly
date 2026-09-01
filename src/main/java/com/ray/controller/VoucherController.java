package com.ray.controller;


import com.ray.dto.Result;
import com.ray.entity.Voucher;
import com.ray.service.IVoucherService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/voucher")
@Tag(name = "优惠券管理")
public class VoucherController {

    @Resource
    private IVoucherService voucherService;

    /**
     * 新增秒杀券
     * @param voucher 优惠券信息，包含秒杀信息
     * @return 优惠券id
     */
    @PostMapping("seckill")
    @Operation(summary = "新增秒杀优惠券", operationId = "createSeckillVoucher")
    public Result addSeckillVoucher(@RequestBody Voucher voucher) {
        voucherService.addSeckillVoucher(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 新增普通券
     * @param voucher 优惠券信息
     * @return 优惠券id
     */
    @PostMapping
    @Operation(summary = "新增普通优惠券", operationId = "createVoucher")
    public Result addVoucher(@RequestBody Voucher voucher) {
        voucherService.save(voucher);
        return Result.ok(voucher.getId());
    }


    /**
     * 查询店铺的优惠券列表
     * @param shopId 店铺id
     * @return 优惠券列表
     */
    @GetMapping("/list/{shopId}")
    @Operation(summary = "查询商户优惠券", operationId = "listShopVouchers")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
       return voucherService.queryVoucherOfShop(shopId);
    }
}
