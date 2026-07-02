package com.foodiego.service;

import com.foodiego.dto.Result;
import com.foodiego.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;
import com.foodiego.entity.VoucherOrder;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author FoodieGo Team
 * @since 2021-12-22
 */
public interface IVoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);

}
