package com.foodiego.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系
 * </p>
 *
 * @author FoodieGo Team
 * @since 2022-01-04
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_seckill_voucher")
@ApiModel(description = "秒杀券配置（与优惠券一对一关联）")
public class SeckillVoucher implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 关联的优惠券的id
     */
    @TableId(value = "voucher_id", type = IdType.INPUT)
    @ApiModelProperty(value = "关联的优惠券ID（主键）", example = "1")
    private Long voucherId;

    /**
     * 库存
     */
    @ApiModelProperty(value = "秒杀库存数量", example = "100")
    private Integer stock;

    /**
     * 创建时间
     */
    @ApiModelProperty(value = "创建时间", hidden = true)
    private LocalDateTime createTime;

    /**
     * 生效时间
     */
    @ApiModelProperty(value = "秒杀开始时间")
    private LocalDateTime beginTime;

    /**
     * 失效时间
     */
    @ApiModelProperty(value = "秒杀结束时间")
    private LocalDateTime endTime;

    /**
     * 更新时间
     */
    @ApiModelProperty(value = "更新时间", hidden = true)
    private LocalDateTime updateTime;


}