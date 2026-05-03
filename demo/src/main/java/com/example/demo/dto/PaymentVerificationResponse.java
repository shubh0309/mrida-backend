package com.example.demo.dto;

import com.example.demo.model.OrderFulfillmentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaymentVerificationResponse {
    private long merchantOrderId;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private OrderFulfillmentStatus fulfillmentStatus;
}
