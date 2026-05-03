package com.example.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CreateCheckoutOrderRequest {

    @Valid
    private DeliveryDetailsRequest delivery;

    @Valid
    @NotEmpty(message = "Cart must contain at least one item")
    private List<CheckoutLineItemDto> items;
}
