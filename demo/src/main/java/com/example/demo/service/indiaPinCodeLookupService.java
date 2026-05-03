package com.example.demo.service;

import com.example.demo.dto.IndiaPinCodeLookupResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Locale;

@Service
public class indiaPinCodeLookupService {
    private static final String LOOKUP_URL = "https://api.postalpincode.in/pincode/";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;
    private final indiaLocationReferenceService locations;

    public indiaPinCodeLookupService(ObjectMapper objectMapper, indiaLocationReferenceService locations) {
        this.objectMapper = objectMapper;
        this.locations = locations;
    }

    public IndiaPinCodeLookupResponse resolve(String pinCode) {
        String cleaned = pinCode == null ? "" : pinCode.trim();
        if (!cleaned.matches("^[1-9]\\d{5}$")) {
            throw new IllegalArgumentException("PIN code must be a valid 6-digit Indian postal code");
        }

        String city;
        String stateName;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            headers.set("Accept", "application/json");
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    LOOKUP_URL + cleaned,
                    HttpMethod.GET,
                    entity,
                    String.class
            );
            String body = response.getBody();
            JsonNode root = objectMapper.readTree(body == null ? "[]" : body);
            JsonNode first = root.isArray() && root.size() > 0 ? root.get(0) : null;
            if (first == null || !"Success".equalsIgnoreCase(first.path("Status").asText())) {
                throw new IllegalArgumentException("Invalid PIN code");
            }

            JsonNode postOffice = first.path("PostOffice");
            JsonNode office = postOffice.isArray() && postOffice.size() > 0 ? postOffice.get(0) : null;
            if (office == null) {
                throw new IllegalArgumentException("Unable to resolve city/state for this PIN code");
            }

            city = office.path("District").asText("").trim();
            if (city.isBlank()) {
                city = office.path("Name").asText("").trim();
            }
            stateName = office.path("State").asText("").trim();
            if (city.isBlank() || stateName.isBlank()) {
                throw new IllegalArgumentException("Unable to resolve city/state for this PIN code");
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new IllegalStateException("PIN code lookup service unavailable: " + ex.getMessage());
        } catch (Exception ex) {
            throw new IllegalStateException("PIN code lookup service unavailable: " + ex.getMessage());
        }

        String stateCode = locations.allStates().stream()
                .filter(s -> s.getName() != null && s.getName().equalsIgnoreCase(stateName))
                .map(s -> s.getCode().toUpperCase(Locale.ROOT))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Resolved state is not supported: " + stateName));

        return new IndiaPinCodeLookupResponse(cleaned, city, stateCode, stateName);
    }
}
