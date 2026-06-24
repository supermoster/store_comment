package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.VoucherOrderDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisConstants;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.hmdp.config.RabbitMQConfig.SECKILL_ORDER_EXCHANGE;
import static com.hmdp.config.RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY;
import static com.hmdp.utils.RedisConstants.MQ_RETRY_ORDER_KEY;

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
    

    // 优雅停机标记，防止 @Scheduled 在关闭期间继续访问 Redis
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

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
        shuttingDown.set(true);  // 先标记停机，阻止 @Scheduled 继续访问 Redis/MySQL
        log.info("开始关闭 MQ 异步发送线程池... 队列中待处理任务: {}",
                ASYNC_MQ_EXECUTOR.getQueue().size());
        try {
            if (!ASYNC_MQ_EXECUTOR.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("线程池未在 10s 内完成，强制关闭，剩余任务: {}",
                        ASYNC_MQ_EXECUTOR.getQueue().size());
                ASYNC_MQ_EXECUTOR.shutdownNow();
            } else {
                log.info("MQ 异步发送线程池已优雅关闭");
            }
        } catch (InterruptedException e) {
            log.error("等待线程池关闭被中断", e);
            ASYNC_MQ_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }


    /**
     * 秒杀券下单
     *
     * 优化点：
     * 1. RedisIdWorker 批量预取ID，消除每次 Redis INCR 调用
     * 2. MQ 异步发送，消除 broker 确认的阻塞等待（~2ms）
     * 3. 异步失败时写入 Redis List 兜底，后续补偿
     * 4. Redis Hash 缓存活动时间，快速校验有效期
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
            long sendStart = System.currentTimeMillis();
            try {
                rabbitTemplate.convertAndSend(SECKILL_ORDER_EXCHANGE, SECKILL_ORDER_ROUTING_KEY, dto);
                long elapsed = System.currentTimeMillis() - sendStart;
                if (elapsed > 50) {
                    log.warn("MQ 发送耗时较长(非排队), orderId={}, elapsed={}ms", orderId, elapsed);
                }
            } catch (Exception e) {
                log.error("MQ 异步发送失败, orderId={}", orderId, e);
                // 写入 Redis 备份队列，定时任务补偿重发
                stringRedisTemplate.opsForList().leftPush(MQ_RETRY_ORDER_KEY,
                        orderId + ":" + voucherId + ":" + userId + ":0");
            }
        });

        // 5. 立即返回
        return Result.ok(orderId);
    }

    /**
     * 定时任务：补偿重发 Redis 备份队列中失败的 MQ 消息
     * 每 30 秒扫描一次，批量弹出并重试，最多重试 3 次
     */
    @Scheduled(fixedDelay = 30_000)
    public void retryMqFromRedis() {
        if (shuttingDown.get()) {
            return;
        }
        String redisKey = MQ_RETRY_ORDER_KEY;
        int batchSize = 100;
        for (int i = 0; i < batchSize; i++) {
            String entry;
            try {
                entry = stringRedisTemplate.opsForList().rightPop(redisKey);
            } catch (Exception e) {
                // 关闭期间 Redis 连接可能中断，静默退出
                if (shuttingDown.get()) {
                    log.info("应用关闭中，停止 Redis 补偿扫描");
                    return;
                }
                log.error("Redis rightPop 异常", e);
                break;
            }
            if (entry == null) {
                // 队列已空
                break;
            }
            // 格式: orderId:voucherId:userId:retryCount
            String[] parts = entry.split(":");
            if (parts.length != 4) {
                log.warn("Redis 备份队列数据格式异常，跳过: {}", entry);
                continue;
            }
            long orderId = Long.parseLong(parts[0]);
            long voucherId = Long.parseLong(parts[1]);
            long userId = Long.parseLong(parts[2]);
            int retryCount = Integer.parseInt(parts[3]);

            VoucherOrderDTO dto = new VoucherOrderDTO();
            dto.setId(orderId);
            dto.setVoucherId(voucherId);
            dto.setUserId(userId);

            try {
                rabbitTemplate.convertAndSend(SECKILL_ORDER_EXCHANGE, SECKILL_ORDER_ROUTING_KEY, dto);
                log.info("补偿重发 MQ 成功, orderId={}, retryCount={}", orderId, retryCount);
            } catch (Exception e) {
                retryCount++;
                log.error("补偿重发 MQ 失败, orderId={}, retryCount={}", orderId, retryCount, e);
                if (retryCount < 3) {
                    // 未达上限，重新放回队列头部
                    stringRedisTemplate.opsForList().leftPush(redisKey,
                            orderId + ":" + voucherId + ":" + userId + ":" + retryCount);
                } else {
                    log.error("补偿重发 MQ 重试耗尽，消息丢弃, orderId={}, voucherId={}, userId={}",
                            orderId, voucherId, userId);
                }
            }
        }
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