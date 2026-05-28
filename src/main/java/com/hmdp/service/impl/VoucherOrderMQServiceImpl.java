package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.VoucherOrderDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.hmdp.config.RabbitMQConfig.SECKILL_ORDER_EXCHANGE;
import static com.hmdp.config.RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY;

@Service
@Slf4j
public class
VoucherOrderMQServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService iSeckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RabbitTemplate rabbitTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill2.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 异步MQ发送线程池 — 核心4线程，最大8线程，有界队列 + CallerRunsPolicy 做背压
    private static final ThreadPoolExecutor ASYNC_MQ_EXECUTOR = new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(5000),
            r -> {
                Thread t = new Thread(r, "async-mq-sender");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @PreDestroy
    public void shutdown() {
        ASYNC_MQ_EXECUTOR.shutdown();
    }

    /**
     * 秒杀券下单
     *
     * 优化点：
     * 1. RedisIdWorker 批量预取ID，消除每次 Redis INCR 调用
     * 2. MQ 异步发送，消除 broker 确认的阻塞等待（~2ms）
     * 3. 异步失败时写入 Redis List 兜底，后续补偿
     */
    @Override
    public Result secKillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        // 1. 执行 Lua 脚本（Redis 原子校验 + 扣库存 + 标记用户）
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(), voucherId.toString(), userId.toString());
        int r = result.intValue();
        if (r == 1) {
            return Result.fail("库存不足");
        }
        if (r == 2) {
            return Result.fail("不能重复下单");
        }
        if (r != 0) {
            return Result.fail("秒杀失败");
        }

        // 2. 生成订单ID（批量预取，无 Redis 调用）
        long orderId = redisIdWorker.nextId("order");

        // 3. 封装 DTO
        VoucherOrderDTO dto = new VoucherOrderDTO();
        dto.setId(orderId);
        dto.setVoucherId(voucherId);
        dto.setUserId(userId);

        // 4. 异步发送 MQ，不阻塞主线程
        ASYNC_MQ_EXECUTOR.submit(() -> {
            try {
                rabbitTemplate.convertAndSend(SECKILL_ORDER_EXCHANGE, SECKILL_ORDER_ROUTING_KEY, dto);
            } catch (Exception e) {
                log.error("MQ 异步发送失败, orderId={}", orderId, e);
                // 写入 Redis 备份队列，定时任务补偿重发
                stringRedisTemplate.opsForList().leftPush("mq:retry:orders",
                        orderId + ":" + voucherId + ":" + userId);
            }
        });

        // 5. 立即返回
        return Result.ok(orderId);
    }

    @Transactional
    public void handleVoucherOrder(VoucherOrderDTO voucherOrderDTO) {
        // 扣减库存（乐观锁）
        boolean updateSuccess = iSeckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrderDTO.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!updateSuccess) {
            log.info("库存不足，订单创建失败: {}", voucherOrderDTO.getId());
            return;
        }
        // 插入订单（唯一索引兜底去重）
        try {
            VoucherOrder voucherOrder = BeanUtil.copyProperties(voucherOrderDTO, VoucherOrder.class);
            save(voucherOrder);
        } catch (DuplicateKeyException e) {
            log.info("重复下单，订单创建失败: {}", voucherOrderDTO.getId());
        }
    }

    @Override
    public Result voucher(Long voucherId) {
        return null;
    }

    @Override
    public void handleVoucherOrder(VoucherOrder voucherOrder) {
    }
}