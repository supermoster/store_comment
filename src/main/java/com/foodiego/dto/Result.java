package com.foodiego.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "统一响应结果")
public class Result {
    @ApiModelProperty(value = "是否成功", example = "true")
    private Boolean success;

    @ApiModelProperty(value = "错误信息（失败时返回）", example = "库存不足")
    private String errorMsg;

    @ApiModelProperty(value = "返回数据")
    private Object data;

    @ApiModelProperty(value = "数据总条数（分页查询时返回）", example = "100")
    private Long total;

    public static Result ok(){
        return new Result(true, null, null, null);
    }
    public static Result ok(Object data){
        return new Result(true, null, data, null);
    }
    public static Result ok(List<?> data, Long total){
        return new Result(true, null, data, total);
    }
    public static Result fail(String errorMsg){
        return new Result(false, errorMsg, null, null);
    }
}