package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RazorpayPaymentVerifyRequest {

    @NotNull
    private Long merchantOrderId;

    @NotBlank
    private String razorpayOrderId;

    @NotBlank
    private String razorpayPaymentId;

    @NotBlank
    private String razorpaySignature;
}
