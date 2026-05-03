package com.example.demo.controller;

import com.razorpay.RazorpayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalApiExceptionAdvice {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        Map<String, Object> payload = base("BAD_REQUEST", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(payload);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConfig(IllegalStateException ex) {
        Map<String, Object> payload = base("CONFIGURATION", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(payload);
    }

    @ExceptionHandler(RazorpayException.class)
    public ResponseEntity<Map<String, Object>> handleRz(RazorpayException ex) {
        Map<String, Object> payload = base("RAZORPAY_ERROR",
                firstNonBlank(ex.getMessage(), "Razorpay API error"));
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(payload);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        Map<String, Object> payload = base("VALIDATION_FAILED", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(payload);
    }

    private static Map<String, Object> base(String code, String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("message", message == null ? "Unexpected error" : message);
        return map;
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary == null || primary.isBlank()) return fallback;
        return primary;
    }
}
