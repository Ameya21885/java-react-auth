package com.example.java_react_auth.module.auth.service.impl;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MemoryOtpService {

    private final Map<String, OtpData> otpMap = new ConcurrentHashMap<>();

    @Data
    @AllArgsConstructor
    private static class OtpData {
        private String code;
        private LocalDateTime expiry;
    }

    public void saveOtp(String identifier, String code) {
        otpMap.put(identifier, new OtpData(code, LocalDateTime.now().plusMinutes(5)));
    }

    public boolean verifyOtp(String identifier, String code) {
        OtpData data = otpMap.get(identifier);
        if (data != null && data.getCode().equals(code) && data.getExpiry().isAfter(LocalDateTime.now())) {
            otpMap.remove(identifier); // One-time use
            return true;
        }
        return false;
    }
}
