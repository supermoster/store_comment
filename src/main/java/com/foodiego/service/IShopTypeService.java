package com.foodiego.service;

import com.foodiego.dto.Result;
import com.foodiego.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author FoodieGo Team
 * @since 2021-12-22
 */
public interface IShopTypeService extends IService<ShopType> {

    /**
     * 查询所有商铺类型
     * @return
     */
    Result queryTypeList();
}
