package com.example.demo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CheckoutLineItemDto {
    @NotNull
    private Integer productId;

    @Min(1)
    private int quantity;
}
