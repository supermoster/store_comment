package com.foodiego.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.foodiego.dto.Result;
import com.foodiego.entity.ShopType;
import com.foodiego.mapper.ShopTypeMapper;
import com.foodiego.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.foodiego.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author FoodieGo Team
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 查询所有商铺类型
     * @return
     */
    @Override
    public Result queryTypeList() {
        String jsonShopTypekey = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_TYPE_KEY);

        if (StrUtil.isNotBlank(jsonShopTypekey)) {
            return Result.ok(JSONUtil.toList(jsonShopTypekey, ShopType.class));
        }

        List<ShopType> shopTypes = query().orderByAsc("sort").list();

        if (shopTypes == null) {
            return Result.fail("店铺类型不存在");
        }

        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(shopTypes));

        return Result.ok(shopTypes);
    }
}
