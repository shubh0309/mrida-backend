package com.example.demo.service;

import com.example.demo.dto.IndiaLocationsRoot;
import com.example.demo.dto.IndiaStateCityDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class indiaLocationReferenceService {

    private final Map<String, IndiaStateCityDto> byCode;

    public indiaLocationReferenceService(ObjectMapper objectMapper) {
        IndiaLocationsRoot root;
        try {
            var resource = new ClassPathResource("data/india-locations.json");
            root = objectMapper.readValue(resource.getInputStream(), IndiaLocationsRoot.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to parse classpath:data/india-locations.json", ex);
        }
        List<IndiaStateCityDto> list = root.getStates() == null ? List.of() : root.getStates();
        this.byCode = list.stream()
                .collect(Collectors.toMap(s -> s.getCode().toUpperCase(Locale.ROOT), Function.identity(),
                        (a, b) -> a));
    }

    public List<IndiaStateCityDto> allStates() {
        return byCode.values().stream().sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName())).toList();
    }

    public boolean isValidCombination(String stateCodeUpper, String cityName) {
        if (cityName == null || cityName.isBlank()) return false;
        IndiaStateCityDto state = stateCodeUpper == null ? null : byCode.get(stateCodeUpper.toUpperCase(Locale.ROOT));
        if (state == null || state.getCities() == null) return false;
        String norm = normalize(cityName);
        return state.getCities().stream().map(this::normalize).anyMatch(c -> c.equals(norm));
    }

    private String normalize(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public Optional<IndiaStateCityDto> resolveState(String stateCodeUpper) {
        return Optional.ofNullable(byCode.get(stateCodeUpper.toUpperCase(Locale.ROOT)));
    }

    /** City must match dropdown list unless list is unexpectedly empty — then allow any blank-safe string. */
    public List<String> citiesFor(String stateCodeUpper) {
        return resolveState(stateCodeUpper)
                .map(IndiaStateCityDto::getCities)
                .orElse(Collections.emptyList());
    }
}
