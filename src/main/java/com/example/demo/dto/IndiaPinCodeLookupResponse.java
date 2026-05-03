package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class IndiaPinCodeLookupResponse {
    private String pinCode;
    private String city;
    private String stateCode;
    private String stateName;
}
