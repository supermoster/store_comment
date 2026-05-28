package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.dto.VoucherOrderDTO;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 优惠券下单
     * @param voucherId
     * @return
     */
    Result secKillVoucher(Long voucherId);

    void handleVoucherOrder(VoucherOrder voucherOrder);

    void handleVoucherOrder(VoucherOrderDTO voucherOrderDTO);

    Result voucher(Long voucherId);
}
