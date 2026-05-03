package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class DeliveryDetailsRequest {

    @NotBlank(message = "Recipient name is required")
    private String recipientName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @Email(message = "Valid email is required")
    @NotBlank
    private String email;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @NotBlank(message = "Address line 1 is required")
    private String addressLine1;

    private String addressLine2;

    @NotBlank
    @Pattern(regexp = "^[1-9]\\d{5}$", message = "PIN code must be a valid 6-digit Indian postal code")
    private String pinCode;

    private String landmark;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "State code is required")
    private String stateCode;

    private String stateName;
}
