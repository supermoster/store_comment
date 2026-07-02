package com.foodiego.mapper;

import com.foodiego.entity.Follow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author FoodieGo Team
 * @since 2021-12-22
 */
public interface FollowMapper extends BaseMapper<Follow> {

    @Delete("delete from tb_follow where user_id = #{userId} and follow_user_id = #{followUserId}")
    boolean delete(Long userId, Long followUserId);
}
