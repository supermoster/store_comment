package com.foodiego.utils;

public interface Lock {
    // 尝试获取锁
    boolean tryLock(Long timeoutSec);

    // 释放锁
    void unlock();
}
