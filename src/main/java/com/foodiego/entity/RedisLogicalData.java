package com.foodiego.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RedisLogicalData<T> {
    private T data;
    private LocalDateTime expireTime;
}
