package com.foodiego.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 商铺类型实体
 * </p>
 *
 * @author FoodieGo Team
 * @since 2021-12-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_shop_type")
@ApiModel(description = "商铺类型")
public class ShopType implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "类型主键ID", example = "1")
    private Long id;

    /**
     * 类型名称
     */
    @ApiModelProperty(value = "类型名称", example = "美食")
    private String name;

    /**
     * 图标
     */
    @ApiModelProperty(value = "类型图标")
    private String icon;

    /**
     * 顺序
     */
    @ApiModelProperty(value = "排序序号", example = "1")
    private Integer sort;

    /**
     * 创建时间
     */
    @JsonIgnore
    @ApiModelProperty(value = "创建时间", hidden = true)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @JsonIgnore
    @ApiModelProperty(value = "更新时间", hidden = true)
    private LocalDateTime updateTime;


}