package com.foodiego.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RedisIdWorker} — batch-prefetch global ID generation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisIdWorker Unit Tests")
class RedisIdWorkerTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RedisIdWorker redisIdWorker;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("First call triggers Redis INCR for batch prefetch (BATCH_SIZE=100)")
    void nextId_FirstCall_PrefetchesBatchFromRedis() {
        when(valueOperations.increment(anyString(), eq(100L))).thenReturn(100L);

        long id = redisIdWorker.nextId("order");

        assertTrue(id > 0, "Generated ID should be positive");
        // Verify Redis INCR was called exactly once for batch prefetch
        verify(valueOperations, times(1)).increment(anyString(), eq(100L));
    }

    @Test
    @DisplayName("Subsequent calls (up to BATCH_SIZE) do not hit Redis")
    void nextId_WithinBatch_NoRedisCall() {
        when(valueOperations.increment(anyString(), eq(100L))).thenReturn(100L);

        // First call triggers batch fetch
        redisIdWorker.nextId("order");
        verify(valueOperations, times(1)).increment(anyString(), eq(100L));

        // Next 50 calls should use the batch, no additional Redis calls
        for (int i = 0; i < 50; i++) {
            long id = redisIdWorker.nextId("order");
            assertTrue(id > 0, "ID " + i + " should be positive");
        }

        // Still only one Redis call
        verify(valueOperations, times(1)).increment(anyString(), eq(100L));
    }

    @Test
    @DisplayName("After batch exhausted, refetches from Redis")
    void nextId_ExhaustsBatch_Refetches() {
        when(valueOperations.increment(anyString(), eq(100L)))
                .thenReturn(100L)   // first batch
                .thenReturn(200L);  // second batch

        // Consume first batch (100 IDs)
        redisIdWorker.nextId("order");
        for (int i = 0; i < 99; i++) {
            redisIdWorker.nextId("order");
        }

        // Next call triggers refetch
        redisIdWorker.nextId("order");

        verify(valueOperations, times(2)).increment(anyString(), eq(100L));
    }

    @Test
    @DisplayName("Concurrent ID generation: all IDs unique across 50 threads × 100 calls")
    void concurrentNextId_GeneratesUniqueIds() throws InterruptedException {
        // Need enough batches for 50 threads × 100 calls = 5000 IDs
        // 5000 / 100 = 50 batches; add 10 extra for safety
        Long[] batches = new Long[60];
        for (int i = 0; i < 60; i++) {
            batches[i] = (long) ((i + 1) * 100);
        }
        when(valueOperations.increment(anyString(), eq(100L)))
                .thenReturn(batches[0], java.util.Arrays.copyOfRange(batches, 1, batches.length));

        int threadCount = 50;
        int callsPerThread = 100;
        Set<Long> idSet = ConcurrentHashSet.newSet();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < callsPerThread; i++) {
                        long id = redisIdWorker.nextId("test");
                        idSet.add(id);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        int expectedTotal = threadCount * callsPerThread;
        assertEquals(expectedTotal, idSet.size(),
                "All " + expectedTotal + " generated IDs should be unique");
    }

    @Test
    @DisplayName("Generated ID format: timestamp in high 32 bits, sequence in low 32 bits")
    void buildId_FormatCorrect() {
        when(valueOperations.increment(anyString(), eq(100L))).thenReturn(100L);

        long id = redisIdWorker.nextId("order");

        long timestampPart = id >> 32;
        long sequencePart = id & 0xFFFFFFFFL;

        // Timestamp should be a reasonable value (seconds since 2025-01-01)
        assertTrue(timestampPart > 0, "Timestamp part should be positive");
        // Sequence should be between 1 and 100 (our batch range)
        assertTrue(sequencePart >= 1 && sequencePart <= 100,
                "Sequence should be within batch range, got " + sequencePart);
    }

    @Test
    @DisplayName("Different key prefixes use separate batch caches")
    void nextId_DifferentPrefixes_SeparateBatches() {
        when(valueOperations.increment(anyString(), eq(100L)))
                .thenReturn(100L)
                .thenReturn(500L);

        long orderId = redisIdWorker.nextId("order");
        long blogId = redisIdWorker.nextId("blog");

        assertNotEquals(orderId, blogId, "Different prefixes should produce different IDs");
        // Each prefix triggers its own Redis INCR
        verify(valueOperations, times(2)).increment(anyString(), eq(100L));
    }

    /**
     * Simple thread-safe HashSet wrapper since Java 8 doesn't have ConcurrentHashMap.newKeySet()
     * with the right generics.
     */
    private static class ConcurrentHashSet {
        static Set<Long> newSet() {
            return java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
        }
    }
}
