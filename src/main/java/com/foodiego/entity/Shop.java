package com.foodiego.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
 * 商铺实体
 * </p>
 *
 * @author FoodieGo Team
 * @since 2021-12-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_shop")
@ApiModel(description = "商铺信息")
public class Shop implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "商铺主键ID", example = "1")
    private Long id;

    /**
     * 商铺名称
     */
    @ApiModelProperty(value = "商铺名称", example = "海底捞火锅")
    private String name;

    /**
     * 商铺类型的id
     */
    @ApiModelProperty(value = "商铺类型ID", example = "1")
    private Long typeId;

    /**
     * 商铺图片，多个图片以','隔开
     */
    @ApiModelProperty(value = "商铺图片，多张以逗号分隔")
    private String images;

    /**
     * 商圈，例如陆家嘴
     */
    @ApiModelProperty(value = "商圈", example = "武林广场")
    private String area;

    /**
     * 地址
     */
    @ApiModelProperty(value = "详细地址", example = "杭州市下城区延安路123号")
    private String address;

    /**
     * 经度
     */
    @ApiModelProperty(value = "经度", example = "120.158")
    private Double x;

    /**
     * 维度
     */
    @ApiModelProperty(value = "纬度", example = "30.276")
    private Double y;

    /**
     * 均价，取整数
     */
    @ApiModelProperty(value = "人均价格（元）", example = "150")
    private Long avgPrice;

    /**
     * 销量
     */
    @ApiModelProperty(value = "销量", example = "3600")
    private Integer sold;

    /**
     * 评论数量
     */
    @ApiModelProperty(value = "评论数量", example = "128")
    private Integer comments;

    /**
     * 评分，1~5分，乘10保存，避免小数
     */
    @ApiModelProperty(value = "评分（1~5分，乘10存储，如45表示4.5分）", example = "45")
    private Integer score;

    /**
     * 营业时间，例如 10:00-22:00
     */
    @ApiModelProperty(value = "营业时间", example = "10:00-22:00")
    private String openHours;

    /**
     * 创建时间
     */
    @ApiModelProperty(value = "创建时间", hidden = true)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @ApiModelProperty(value = "更新时间", hidden = true)
    private LocalDateTime updateTime;


    @TableField(exist = false)
    @ApiModelProperty(value = "距离用户位置（km），非数据库字段", example = "1.5")
    private Double distance;
}