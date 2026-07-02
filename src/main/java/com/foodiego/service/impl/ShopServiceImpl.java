package com.foodiego.service.impl;

import cn.hutool.cache.Cache;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodiego.dto.Result;
import com.foodiego.entity.RedisLogicalData;
import com.foodiego.entity.Shop;
import com.foodiego.mapper.ShopMapper;
import com.foodiego.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.foodiego.utils.CacheClient;
import com.foodiego.utils.RedisConstants;
import com.foodiego.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.sql.Array;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author FoodieGo Team
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    /**
     * 根据id查询商铺信息
     *
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @Override
    public Result queryById(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String jsonShop = stringRedisTemplate.opsForValue().get(key);

        // 1. 防穿透：命中了缓存的空值（之前查过，DB 不存在）
        if ("".equals(jsonShop)) {
            return Result.fail("店铺不存在");
        }

        // 2. 缓存未命中，查 DB 并创建逻辑过期缓存
        if (StrUtil.isBlank(jsonShop)) {
            Shop shop = getById(id);
            if (shop == null) {
                // 缓存空值，防止穿透（下次请求直接返回"不存在"，不穿到 DB）
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return Result.fail("店铺不存在");
            }
            cacheClient.setWithLogicalExpire(key, shop, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return Result.ok(shop);
        }
        // 3. 缓存命中 → 逻辑过期防击穿（过期时互斥重建，旧数据兜底）
        Shop shop = cacheClient.queryWithLogicalExpire(
                RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById,
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }


    /**
     * 更新商铺信息
     *
     * @param shop 商铺数据
     * @return 无
     */
    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        // 检查店铺id
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 更新数据库
        updateById(shop);

        // 删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());

        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 判断是否需要根据距离查询
        if (x == null || y == null) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 获取当前页数据
            List<Shop> records = page.getRecords();
            return Result.ok(records);
        }
        // 计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 查询Redis，按照距离排序，分页 结果：shopId，distance
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH KEY BYLONLAT X Y BYRADIUS 5 WHICHDISTANCE ASC LIMIT 0 10
                .search(key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 判断result是否为空
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        // 解析出shopId
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();

        // 如果要截取的数据大于查询的数据，则直接返回空
        if (list.size() <= from) {
            return Result.ok(Collections.emptyList());
        }
        // 截取from ~ end的数据
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            String shopId = result.getContent().getName();
            ids.add(Long.valueOf(shopId));
            
            Distance distance = result.getDistance();
            distanceMap.put(shopId, distance);
        });
        // 根据shopId查询
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ") ").list();

        // 返回数据
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }
}
