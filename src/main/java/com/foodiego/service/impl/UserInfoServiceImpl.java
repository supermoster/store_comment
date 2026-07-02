package com.foodiego.service.impl;

import com.foodiego.entity.UserInfo;
import com.foodiego.mapper.UserInfoMapper;
import com.foodiego.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author FoodieGo Team
 * @since 2021-12-24
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
