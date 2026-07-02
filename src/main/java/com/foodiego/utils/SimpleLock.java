package com.foodiego.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleLock implements Lock{
    // 锁名称
    private String name;

    private StringRedisTemplate stringRedisTemplate;

    public SimpleLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString() + "-";
    // 释放锁的lua脚本
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
         UNLOCK_SCRIPT = new DefaultRedisScript<>();
         UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
         UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(Long timeoutSec) {
        // 当前线程ID
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean flag = stringRedisTemplate
                .opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId,timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag); // 防止自动装箱插箱造成空指针异常
    }

    // 基于lua脚本实现释放锁
    @Override
    public void unlock() {
        // 获取锁
        // 调用lua脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }

//    @Override
//    public void unlock() {
//        // 当前线程ID
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        // 获取锁
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        // 判断锁是否ours
//        if (threadId.equals(id)) {
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }
}
