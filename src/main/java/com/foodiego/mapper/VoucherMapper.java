package com.foodiego.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodiego.entity.Voucher;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author FoodieGo Team
 * @since 2021-12-22
 */
public interface VoucherMapper extends BaseMapper<Voucher> {

    List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId);
}
