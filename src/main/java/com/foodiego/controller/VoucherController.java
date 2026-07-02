package com.foodiego.controller;


import com.foodiego.dto.Result;
import com.foodiego.entity.Voucher;
import com.foodiego.service.IVoucherService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  优惠券管理接口
 * </p>
 *
 * @author FoodieGo Team
 * @since 2021-12-22
 */
@Api(tags = "优惠券管理")
@RestController
@RequestMapping("/voucher")
public class VoucherController {

    @Resource
    private IVoucherService voucherService;

    /**
     * 新增普通券
     * @param voucher 优惠券信息
     * @return 优惠券id
     */
    @ApiOperation("新增普通优惠券")
    @PostMapping
    public Result addVoucher(@ApiParam(value = "优惠券信息", required = true) @RequestBody Voucher voucher) {
        voucherService.save(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 新增秒杀券
     * @param voucher 优惠券信息，包含秒杀信息
     * @return 优惠券id
     */
    @ApiOperation("新增秒杀券 - 同时写入普通券和秒杀券表")
    @PostMapping("seckill")
    public Result addSeckillVoucher(@ApiParam(value = "秒杀券信息（含秒杀时间、库存）", required = true) @RequestBody Voucher voucher) {
        voucherService.addSeckillVoucher(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 查询店铺的优惠券列表
     * @param shopId 店铺id
     * @return 优惠券列表
     */
    @ApiOperation("查询指定店铺的优惠券列表")
    @GetMapping("/list/{shopId}")
    public Result queryVoucherOfShop(@ApiParam(value = "店铺ID", required = true) @PathVariable("shopId") Long shopId) {
       return voucherService.queryVoucherOfShop(shopId);
    }
}