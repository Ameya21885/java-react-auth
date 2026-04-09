package com.example.java_react_auth.module.auth.dto;

import lombok.Data;

@Data
public class OtpVerifyRequest {
    private String identifier;
    private String otp;
}
