package com.foodiego.service.impl;

import cn.hutool.json.JSONUtil;
import com.foodiego.dto.Result;
import com.foodiego.entity.Shop;
import com.foodiego.mapper.ShopMapper;
import com.foodiego.utils.CacheClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static com.foodiego.utils.RedisConstants.CACHE_SHOP_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ShopServiceImpl} — shop query with cache-aside pattern.
 * <p>
 * Uses {@code @SpyBean} on the service to selectively mock MyBatis-Plus parent methods
 * (e.g., {@code getById()}, {@code updateById()}) while testing real service logic.
 */
@SpringBootTest(classes = {ShopServiceImpl.class, ShopMapper.class})
@DisplayName("ShopServiceImpl Unit Tests")
class ShopServiceImplTest {

    @SpyBean
    private ShopServiceImpl shopService;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private CacheClient cacheClient;

    @MockBean
    private ShopMapper shopMapper;

    private ValueOperations<String, String> valueOps;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ────────────────────── queryById() tests ──────────────────────

    @Test
    @DisplayName("Cache hit (valid JSON) → delegates to cacheClient.queryWithLogicalExpire for breakdown protection")
    void queryById_CacheHit_DelegatesToLogicalExpireHandler() {
        Shop shop = createShop(1L, "Golden Dim Sum");
        String shopJson = JSONUtil.toJsonStr(shop);
        when(valueOps.get(CACHE_SHOP_KEY + 1)).thenReturn(shopJson);
        when(cacheClient.queryWithLogicalExpire(anyString(), anyLong(), eq(Shop.class),
                any(), anyLong(), any())).thenReturn(shop);

        Result result = shopService.queryById(1L);

        assertTrue(result.getSuccess());
        assertEquals(shop, result.getData());
        verify(cacheClient).queryWithLogicalExpire(
                eq(CACHE_SHOP_KEY), eq(1L), eq(Shop.class), any(), anyLong(), any());
        // Should NOT hit DB
        verify(shopService, never()).getById(anyLong());
    }

    @Test
    @DisplayName("Cache hit empty string (penetration guard from prior null-DB lookup) → returns 'not found'")
    void queryById_CacheHitEmptyString_ReturnsNotFound() {
        when(valueOps.get(CACHE_SHOP_KEY + 1)).thenReturn("");

        Result result = shopService.queryById(1L);

        assertFalse(result.getSuccess());
        assertEquals("店铺不存在", result.getErrorMsg());
        // Must not hit DB for empty-string cache entries
        verify(shopService, never()).getById(anyLong());
    }

    @Test
    @DisplayName("Cache miss + DB found → stores logical-expire cache and returns shop")
    void queryById_CacheMiss_DbFound_CreatesCache() {
        Shop shop = createShop(1L, "New Bistro");
        when(valueOps.get(CACHE_SHOP_KEY + 1)).thenReturn(null);
        doReturn(shop).when(shopService).getById(1L);

        Result result = shopService.queryById(1L);

        assertTrue(result.getSuccess());
        assertEquals(shop, result.getData());
        verify(cacheClient).setWithLogicalExpire(eq(CACHE_SHOP_KEY + 1), eq(shop), anyLong(), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("Cache miss + DB not found → caches empty string (anti-penetration) and returns 'not found'")
    void queryById_CacheMiss_DbNotFound_CachesNull() {
        when(valueOps.get(CACHE_SHOP_KEY + 999)).thenReturn(null);
        doReturn(null).when(shopService).getById(999L);

        Result result = shopService.queryById(999L);

        assertFalse(result.getSuccess());
        assertEquals("店铺不存在", result.getErrorMsg());
        // Verify empty string cached with TTL (penetration protection)
        verify(valueOps).set(eq(CACHE_SHOP_KEY + 999), eq(""), eq(2L), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("Logical-expire handler returns null → 'not found'")
    void queryById_LogicalExpireReturnsNull_ReturnsNotFound() {
        String shopJson = JSONUtil.toJsonStr(createShop(1L, "X"));
        when(valueOps.get(CACHE_SHOP_KEY + 1)).thenReturn(shopJson);
        when(cacheClient.queryWithLogicalExpire(anyString(), anyLong(), eq(Shop.class),
                any(), anyLong(), any())).thenReturn(null);

        Result result = shopService.queryById(1L);

        assertFalse(result.getSuccess());
        assertEquals("店铺不存在", result.getErrorMsg());
    }

    // ────────────────────── updateShop() tests ──────────────────────

    @Test
    @DisplayName("Update with valid ID → calls updateById then deletes cache key")
    void updateShop_ValidId_DeletesCache() {
        Shop shop = createShop(1L, "Updated Bistro");
        doReturn(true).when(shopService).updateById(shop);

        Result result = shopService.updateShop(shop);

        assertTrue(result.getSuccess());
        verify(shopService).updateById(shop);
        verify(stringRedisTemplate).delete(CACHE_SHOP_KEY + 1);
    }

    @Test
    @DisplayName("Update with null ID → returns fail, no DB update, no cache delete")
    void updateShop_NullId_ReturnsFail() {
        Shop shop = new Shop();
        shop.setId(null);

        Result result = shopService.updateShop(shop);

        assertFalse(result.getSuccess());
        assertEquals("店铺id不能为空", result.getErrorMsg());
        verify(shopService, never()).updateById(any());
        verify(stringRedisTemplate, never()).delete(anyString());
    }

    // ────────────────────── Helper ──────────────────────

    private Shop createShop(Long id, String name) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setName(name);
        shop.setTypeId(1L);
        shop.setArea("Downtown");
        shop.setAddress("456 Food St");
        return shop;
    }
}
