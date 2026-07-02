package com.foodiego.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.foodiego.dto.LoginFormDTO;
import com.foodiego.dto.Result;
import com.foodiego.dto.UserDTO;
import com.foodiego.entity.User;
import com.foodiego.mapper.UserMapper;
import com.foodiego.service.IUserService;
import com.foodiego.utils.RedisConstants;
import com.foodiego.utils.RegexUtils;
import com.foodiego.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.foodiego.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author FoodieGo Team
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 发送手机验证码
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号是否合法
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 保存验证码到redis
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code);
        stringRedisTemplate.expire(RedisConstants.LOGIN_CODE_KEY + phone, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 发送验证码
        log.info("验证码为：" + code);

        // 返回结果
        return Result.ok(code);

    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 校验手机号是否合法
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            return Result.fail("手机号格式错误！");
        }

        // 校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误！");
        }

        // 根据手机号查询用户
        User user = query().eq("phone", loginForm.getPhone()).one();

        // 用户不存在，创建新用户
        if (user == null) {
             user = createNewUser(loginForm.getPhone());
             save(user);
        }

        // 拷贝用户信息到UserDTO
//        UserDTO userDTO = new UserDTO();
//        BeanUtil.copyProperties(user,userDTO);

        // 保存UserDTO信息到redis中
        // 随机生成token令牌 UUID
        String token = UUID.randomUUID().toString(true);

        // 将user对象转成hashMap
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
        // 将map中的value转成string
        Map<String, String> stringUserMap = userMap.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue() != null ? entry.getValue().toString() : null
        ));


        // 存储到Redis中
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, stringUserMap);

        // 设置过期时间
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 返回结果
        return Result.ok(token);
    }

    @Override
    public Result logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY + token);
        return Result.ok("登出成功");
    }

    @Override
    public Result sign() {
        // 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 获取当前日期
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern(":yyyy/MM"));
        // 拼接key
        String key = RedisConstants.USER_SIGN_KEY + userId + date;
        // 获取当前是这个月第几天
        int dayOfMonth = now.getDayOfMonth(); // 1 - 31
        // 写入Redis中 命令：SETBIT KEY OFFSET VALUE
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);

        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 获取当前日期
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern(":yyyy/MM"));
        // 拼接key
        String key = RedisConstants.USER_SIGN_KEY + userId + date;
        // 获取当前是这个月第几天
        int dayOfMonth = now.getDayOfMonth(); // 1 - 31
        // 获取这个月到今天为止的签到数据 十进制
        List<Long> list = stringRedisTemplate.opsForValue()
                .bitField(key,
                        BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
                );
        if (list == null || list.isEmpty()) {
            return Result.ok(0);
        }
        Long num = list.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // 遍历循环 num与1进行位运算，判断这个用户今天是否签到
        int count = 0;
        while(true) {
            if ((num & 1) == 0) {
                break;
            }else {
                count++;
            }
            num >>>= 1; // 逻辑右移
        }
        return Result.ok(count);
    }

    // 创建新用户
    private User createNewUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+ RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
