package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TrackOrderItemResponse {
    private long merchantOrderId;
    private String status;
    private String placedOn;
    private String expectedDeliveryDate;
}
