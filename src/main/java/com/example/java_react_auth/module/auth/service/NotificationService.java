package com.example.java_react_auth.module.auth.service;

public interface NotificationService {
    void sendOtp(String target, String otp);
    boolean supports(String target);
}
