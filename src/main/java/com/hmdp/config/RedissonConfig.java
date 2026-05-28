package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;

@Configuration
public class RedissonConfig {

    private RedissonClient redissonClient;

    @Bean
    public RedissonClient redissonClient(){
        // 配置类
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.150.100:6379").setPassword("123456");

        // 创建RedissonClient对象
        redissonClient = Redisson.create(config);
        return redissonClient;
    }

    @PreDestroy
    public void shutdown() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }
}
