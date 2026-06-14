package com.hmdp.controller;


import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Autowired
    private IVoucherOrderService iVoucherOrderService;
    @Autowired
    private StringRedisTemplate redisTemplate;


    /**
     * 秒杀接口限流降级处理
     */
    public Result seckillBlockHandler(Long voucherId, com.alibaba.csp.sentinel.slots.block.BlockException ex) {
        return Result.fail("系统繁忙，请稍后再试");
    }

    /**
     * 秒杀券下单
     * @param voucherId
     * @return
     */
    @PostMapping("seckill/{id}")
    @SentinelResource(value = "seckillVoucher", blockHandler = "seckillBlockHandler")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) throws InterruptedException {
        return iVoucherOrderService.secKillVoucher(voucherId);
    }

    /**
     * 优惠券下单
     * @param voucherId
     * @return
     */
    @PostMapping("/{voucherId}")
    public Result voucher(@PathVariable("voucherId") Long voucherId) {
        return iVoucherOrderService.voucher(voucherId);
    }

    @GetMapping("/ping-redis")
    public String ping() {
        long start = System.nanoTime();
        redisTemplate.opsForValue().increment("ping", 1);
        long cost = (System.nanoTime() - start) / 1_000_000;
        return "cost: " + cost + " ms";
    }
}
