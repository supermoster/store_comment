package com.foodiego.handler;

import com.foodiego.dto.VoucherOrderDTO;
import com.foodiego.service.IVoucherOrderService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RabbitMQHandler} — MQ consumer with retry and dead-letter compensation.
 * Pure Mockito test: the handler uses constructor injection via {@code @RequiredArgsConstructor}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RabbitMQHandler Unit Tests")
class RabbitMQHandlerTest {

    @Mock
    private IVoucherOrderService voucherOrderService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private Channel channel;

    @InjectMocks
    private RabbitMQHandler rabbitMQHandler;

    private VoucherOrderDTO testDto;
    private Message testMessage;
    private MessageProperties messageProperties;

    @BeforeEach
    void setUp() {
        testDto = new VoucherOrderDTO();
        testDto.setId(12345L);
        testDto.setVoucherId(100L);
        testDto.setUserId(1L);

        messageProperties = new MessageProperties();
        testMessage = new Message("{}".getBytes(), messageProperties);
    }

    // ──────────────────── Normal queue consumer ────────────────────

    @Test
    @DisplayName("Successful processing → basicAck")
    void handleVoucherOrder_Success_Acks() throws IOException {
        doNothing().when(voucherOrderService).handleVoucherOrder(testDto);

        rabbitMQHandler.handleVoucherOrder(testMessage, channel, testDto);

        verify(channel).basicAck(anyLong(), eq(false));
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Exception with retry-count=null (default 0) → NACK with requeue, increment header")
    void handleVoucherOrder_ExceptionRetry0_NacksWithRequeue() throws IOException {
        doThrow(new RuntimeException("DB error")).when(voucherOrderService).handleVoucherOrder(testDto);

        rabbitMQHandler.handleVoucherOrder(testMessage, channel, testDto);

        verify(channel).basicNack(anyLong(), eq(false), eq(true));
        // Header should be set to 1
        Object updatedCount = testMessage.getMessageProperties().getHeader("retry-count");
        assertEquals(1, updatedCount);
    }

    @Test
    @DisplayName("Exception with retry-count=2 → NACK with requeue, header incremented to 3")
    void handleVoucherOrder_ExceptionRetry2_IncrementsHeader() throws IOException {
        messageProperties.setHeader("retry-count", 2);
        doThrow(new RuntimeException("DB error")).when(voucherOrderService).handleVoucherOrder(testDto);

        rabbitMQHandler.handleVoucherOrder(testMessage, channel, testDto);

        verify(channel).basicNack(anyLong(), eq(false), eq(true));
        assertEquals(Integer.valueOf(3), testMessage.getMessageProperties().getHeader("retry-count"));
    }

    @Test
    @DisplayName("Exception with retry-count=3 → NACK without requeue (routes to DLX)")
    void handleVoucherOrder_ExceptionRetry3_NacksToDlx() throws IOException {
        messageProperties.setHeader("retry-count", 3);
        doThrow(new RuntimeException("exhausted")).when(voucherOrderService).handleVoucherOrder(testDto);

        rabbitMQHandler.handleVoucherOrder(testMessage, channel, testDto);

        // requeue=false → message goes to dead-letter exchange
        verify(channel).basicNack(anyLong(), eq(false), eq(false));
    }

    @Test
    @DisplayName("Exception with retry-count=5 (>3) → NACK to DLX")
    void handleVoucherOrder_ExceptionRetryExceeded_NacksToDlx() throws IOException {
        messageProperties.setHeader("retry-count", 5);
        doThrow(new RuntimeException("exhausted")).when(voucherOrderService).handleVoucherOrder(testDto);

        rabbitMQHandler.handleVoucherOrder(testMessage, channel, testDto);

        verify(channel).basicNack(anyLong(), eq(false), eq(false));
    }

    // ──────────────────── Dead-letter queue consumer ────────────────────

    @Test
    @DisplayName("Dead letter handler: restocks Redis (INCR stock + SREM dedup + LPUSH dead log) + basicAck")
    void handleDeadLetter_Success_RestocksRedisAndAcks() throws IOException {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        SetOperations<String, String> setOps = mock(SetOperations.class);
        ListOperations<String, String> listOps = mock(ListOperations.class);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOps);
        when(stringRedisTemplate.opsForList()).thenReturn(listOps);

        rabbitMQHandler.handleDeadLetter(testMessage, channel, testDto);

        // 1. Restock: INCR seckill:stock:100
        verify(valueOps).increment("seckill:stock:100", 1L);
        // 2. Clear dedup: SREM seckill:order:100 1
        verify(setOps).remove("seckill:order:100", "1");
        // 3. Log dead order: LPUSH seckill:dead:orders
        verify(listOps).leftPush("seckill:dead:orders", "12345:100:1");
        // 4. Acknowledge the dead-letter message
        verify(channel).basicAck(anyLong(), eq(false));
    }

    @Test
    @DisplayName("Dead letter handler: Redis failure → NACK without requeue (no retry for DLX)")
    void handleDeadLetter_RedisFails_NacksWithoutRequeue() throws IOException {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        doThrow(new RuntimeException("Redis unavailable")).when(valueOps)
                .increment(anyString(), anyLong());

        rabbitMQHandler.handleDeadLetter(testMessage, channel, testDto);

        // Even on failure, DLX handler does NOT retry — it NACKs without requeue
        verify(channel).basicNack(anyLong(), eq(false), eq(false));
    }
}
