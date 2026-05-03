package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndiaLocationsRoot {
    private List<IndiaStateCityDto> states;
}
