package com.example.FoodTourApp.DTO.WalletDTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MomoCallbackDTO {

    private String partnerCode;
    private String orderId;
    private String requestId;
    private Long amount;
    private String orderInfo;
    private String orderType;
    private Long transId;           // Transaction ID từ MOMO
    private Integer resultCode;     // 0: success, khác 0: failed
    private String message;
    private String payType;
    private Long responseTime;
    private String extraData;
    private String signature;
}

