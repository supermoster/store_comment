package com.foodiego.utils;

import cn.hutool.core.bean.BeanUtil;
import com.foodiego.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        if (token == null || token.isEmpty()) {
            return true;
        }

        String tokenkey = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenkey);

        if (userMap.isEmpty()) {
            return true;
        }
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);

        stringRedisTemplate.expire(tokenkey, RedisConstants.LOGIN_USER_TTL, java.util.concurrent.TimeUnit.MINUTES);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 秒杀路径不清理，避免与 LoginInterceptor 双重 remove
        String uri = request.getRequestURI();
        if (uri == null || !uri.contains("/voucher-order/")) {
            UserHolder.removeUser();
        }
    }
}
