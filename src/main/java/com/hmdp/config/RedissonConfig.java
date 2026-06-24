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
    static final String REDISSON_CONFIG_DEV = "redis://192.168.150.100:6379";
    static final String REDISSON_CONFIG_PROD = "redis://redis:6379";
    static final String REDISSON_PASSWORD = "123456";

    @Bean
    public RedissonClient redissonClient(){
        // 配置类
        Config config = new Config();
        config.useSingleServer().setAddress(REDISSON_CONFIG_PROD).setPassword(REDISSON_PASSWORD);

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
