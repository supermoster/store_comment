package com.foodiego.service.impl;

import com.foodiego.entity.BlogComments;
import com.foodiego.mapper.BlogCommentsMapper;
import com.foodiego.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author FoodieGo Team
 * @since 2021-12-22
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
