package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RedisIdWorker {
    // 2025-01-01 00:00:00 UTC 的 epoch 秒数
    private static final long BEGIN_TIMESTAMP = 1735689600L;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd");
    private static final int BATCH_SIZE = 100;

    private final StringRedisTemplate stringRedisTemplate;
    private final ConcurrentHashMap<String, IdBatch> batchCache = new ConcurrentHashMap<>();

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 生成全局唯一 ID（雪花算法变体：时间戳高位 + 序列号低位）
     * 优化：用 System.currentTimeMillis() 替代 LocalDateTime.now()，避免 Windows 下系统调用开销
     */
    public long nextId(String keyPrefix) {
        IdBatch batch = batchCache.computeIfAbsent(keyPrefix, k -> new IdBatch());
        long count = batch.next();
        if (count > 0) {
            return buildId(count);
        }
        synchronized (batch) {
            count = batch.next();
            if (count > 0) {
                return buildId(count);
            }
            String date = LocalDate.now().format(DATE_FORMATTER);
            String key = "icr:" + keyPrefix + ":" + date;
            Long end = stringRedisTemplate.opsForValue().increment(key, BATCH_SIZE);
            batch.reset(end - BATCH_SIZE + 1, end);
        }
        return buildId(batch.next());
    }

    private long buildId(long count) {
        // System.currentTimeMillis() 比 LocalDateTime.now() 轻量得多（Windows 下尤其明显）
        long nowSecond = System.currentTimeMillis() / 1000;
        return (nowSecond - BEGIN_TIMESTAMP) << 32 | count;
    }

    private static class IdBatch {
        private final AtomicLong counter = new AtomicLong(1);
        private volatile long max;

        long next() {
            long c;
            do {
                c = counter.get();
                if (c > max) return -1;
            } while (!counter.compareAndSet(c, c + 1));
            return c;
        }

        void reset(long start, long end) {
            this.counter.set(start);
            this.max = end;
        }
    }
}