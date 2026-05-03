package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CheckoutOrderCreatedResponse {
    /** Internal database identifier for reconciliation and verify endpoint. */
    private long merchantOrderId;
    private String razorpayOrderId;
    /** Amount Razorpay expects in paise. */
    private long amountInPaise;
    private String currency;
    /** Key id safe to expose in browser for Checkout modal. */
    private String razorpayKeyId;
}
