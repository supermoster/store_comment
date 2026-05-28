package com.hmdp.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class VoucherOrderDTO implements Serializable {

    private final static long serialVersionUID = 1L;

    private Long id;
    private Long voucherId;
    private Long userId;
}
