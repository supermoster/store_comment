package com.foodiego.config;

import com.foodiego.utils.LoginInterceptor;
import com.foodiego.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class SpringMVC implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/springfox-swagger-ui/")
                .resourceChain(false);
    }


    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(new LoginInterceptor()).excludePathPatterns(
                "/user/code",
                "/user/login",
                "/blog/hot",
                "/shop/**",
                "/shop-type/**",
                "/upload/**",
                "/voucher/**",
                "/voucher-order/**",  // 秒杀高并发路径，已在 RefreshTokenInterceptor 完成鉴权
                "/swagger-ui/**",
                "/swagger-resources/**",
                "/v3/api-docs/**"
        ).order(1);

        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);
    }
}
