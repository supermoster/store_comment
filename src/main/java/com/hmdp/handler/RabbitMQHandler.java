package com.hmdp.handler;

import com.hmdp.dto.VoucherOrderDTO;
import com.hmdp.service.IVoucherOrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static com.hmdp.config.RabbitMQConfig.SECKILL_DLX_QUEUE;
import static com.hmdp.config.RabbitMQConfig.SECKILL_ORDER_QUEUE;

@Component
@Slf4j
@RequiredArgsConstructor
public class RabbitMQHandler {

    private final IVoucherOrderService voucherOrderService;
    private final StringRedisTemplate stringRedisTemplate;

    // ──────────────────── 正常秒杀队列消费 ────────────────────

    @RabbitListener(queues = SECKILL_ORDER_QUEUE)
    public void handleVoucherOrder(Message msg, Channel channel, VoucherOrderDTO voucherOrderDTO) throws IOException {
        long deliveryTag = msg.getMessageProperties().getDeliveryTag();
        try {
            voucherOrderService.handleVoucherOrder(voucherOrderDTO);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("订单处理异常 orderId={}", voucherOrderDTO.getId(), e);
            Object header = msg.getMessageProperties().getHeader("retry-count");
            int retryCount = header != null ? (Integer) header : 0;
            if (retryCount < 3) {
                msg.getMessageProperties().setHeader("retry-count", retryCount + 1);
                channel.basicNack(deliveryTag, false, true);
            } else {
                channel.basicNack(deliveryTag, false, false);
                log.error("订单处理重试耗尽，进入死信队列 orderId={}", voucherOrderDTO.getId());
            }
        }
    }

    // ──────────────────── 死信队列消费：补偿回收 ────────────────────

    /**
     * 死信队列消费者 — 重试耗尽后补偿 Redis 库存和去重标记
     * 进入死信 = DB 未落库，必须把 Redis 里扣掉的资源还回去
     */
    @RabbitListener(queues = SECKILL_DLX_QUEUE)
    public void handleDeadLetter(Message msg, Channel channel, VoucherOrderDTO voucherOrderDTO) throws IOException {
        long deliveryTag = msg.getMessageProperties().getDeliveryTag();
        long voucherId = voucherOrderDTO.getVoucherId();
        long userId = voucherOrderDTO.getUserId();
        long orderId = voucherOrderDTO.getId();

        log.warn("死信队列收到消息, orderId={}, voucherId={}, userId={}", orderId, voucherId, userId);

        try {
            // 1. 回补 Redis 库存
            stringRedisTemplate.opsForValue().increment("seckill:stock:" + voucherId, 1);

            // 2. 移除 Redis 去重标记，允许用户重新下单
            stringRedisTemplate.opsForSet().remove("seckill:order:" + voucherId, String.valueOf(userId));

            // 3. 写入失败记录到 Redis（便于后续人工处理）
            stringRedisTemplate.opsForList().leftPush("seckill:dead:orders",
                    orderId + ":" + voucherId + ":" + userId);

            channel.basicAck(deliveryTag, false);
            log.info("死信补偿完成, orderId={}, 已回补库存+清除下单标记", orderId);

        } catch (Exception e) {
            log.error("死信补偿失败, orderId={}", orderId, e);
            // 死信队列不再重试，直接拒绝（可考虑落到本地文件兜底）
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
