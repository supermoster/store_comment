package com.hmdp.handler;

import com.hmdp.dto.VoucherOrderDTO;
import com.hmdp.service.IVoucherOrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static com.hmdp.config.RabbitMQConfig.SECKILL_ORDER_QUEUE;

@Component
@Slf4j
@RequiredArgsConstructor
public class RabbitMQHandler {

    private final IVoucherOrderService voucherOrderService;

    @RabbitListener(queues = SECKILL_ORDER_QUEUE)
    public void handleVoucherOrder(Message msg, Channel channel, VoucherOrderDTO voucherOrderDTO) throws IOException {
        long deliveryTag = msg.getMessageProperties().getDeliveryTag();
        try {
            voucherOrderService.handleVoucherOrder(voucherOrderDTO);
            channel.basicAck(deliveryTag, false); // ← 成功，确认删除
        } catch (Exception e) {
            log.error("订单处理异常 orderId={}", voucherOrderDTO.getId(), e);
            // 仅对临时性异常重试，最多3次
            int retryCount = msg.getMessageProperties().getHeader("retry-count") != null
                    ? (Integer) msg.getMessageProperties().getHeader("retry-count")
                    : 0;
            if (retryCount < 3) {
                msg.getMessageProperties().setHeader("retry-count", retryCount + 1);
                channel.basicNack(deliveryTag, false, true);  // ← 重新入队，等下次消费
            } else {
                channel.basicNack(deliveryTag, false, false);
                log.error("订单处理重试耗尽，消息丢弃 orderId={}", voucherOrderDTO.getId());  // ← 拒绝，不重新入队（丢弃/死信）
            }
        }
    }
}
