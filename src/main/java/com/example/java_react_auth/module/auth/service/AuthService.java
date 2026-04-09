package com.example.java_react_auth.module.auth.service;

import java.util.List;
import java.util.Random;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.java_react_auth.module.auth.dto.LoginRequest;
import com.example.java_react_auth.module.auth.dto.LoginResponse;
import com.example.java_react_auth.module.auth.dto.RegistrationRequest;
import com.example.java_react_auth.module.auth.dto.ResetPasswordRequest;
import com.example.java_react_auth.module.auth.entity.User;
import com.example.java_react_auth.module.auth.repository.UserRepository;
import com.example.java_react_auth.module.auth.service.impl.MemoryOtpService;
import com.example.java_react_auth.module.auth.util.JwtUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final MemoryOtpService memoryOtpService;
    private final List<NotificationService> notificationServices;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    private String normalizeIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        identifier = identifier.trim();
        // If it's a 10-digit numeric string, prepend +91
        if (identifier.matches("^\\d{10}$")) {
            return "+91" + identifier;
        }
        return identifier;
    }

    @Transactional
    public String sendOtp(String identifier) {
        final String normalizedId = normalizeIdentifier(identifier);
        // 1. Check if user is registered (Optional: depending on flow, but requested originally)
        boolean isRegistered = normalizedId.contains("@")
                ? userRepository.findByEmail(normalizedId).isPresent()
                : userRepository.findByPhoneNumber(normalizedId).isPresent();

        if (!isRegistered) {
            throw new RuntimeException("User not registered with identifier: " + normalizedId);
        }

        // 2. Generating OTP
        String otp = String.format("%06d", new Random().nextInt(1000000));

        // 3. Save OTP to Memory
        memoryOtpService.saveOtp(normalizedId, otp);

        // 4. LOG OTP to Console for development (solves "Authentication failed" blockage)
        System.out.println("========================================");
        System.out.println("OTP for " + normalizedId + " is: " + otp);
        System.out.println("========================================");

        // 5. Send OTP via Notification Service
        try {
            NotificationService service = notificationServices.stream()
                    .filter(s -> s.supports(normalizedId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No notification service found for: " + normalizedId));

            service.sendOtp(normalizedId, otp);
        } catch (Exception e) {
            System.err.println("Failed to send notification: " + e.getMessage());
            return "OTP generated and logged to console, but notification failed: " + e.getMessage();
        }

        return "OTP sent successfully to " + normalizedId;
    }

    public boolean verifyOtp(String identifier, String otp) {
        identifier = normalizeIdentifier(identifier);
        return memoryOtpService.verifyOtp(identifier, otp);
    }

    @Transactional
    public String register(RegistrationRequest request) {
        String email = normalizeIdentifier(request.getEmail());
        String phoneNumber = normalizeIdentifier(request.getPhoneNumber());

        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email is already registered");
        }
        if (userRepository.findByPhoneNumber(phoneNumber).isPresent()) {
            throw new RuntimeException("Phone number is already registered");
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .username(request.getUserName())
                .email(email)
                .phoneNumber(phoneNumber)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .enabled(true)
                .build();

        userRepository.save(user);
        return "Account created successfully!";
    }

    public LoginResponse login(LoginRequest request) {
        String email = normalizeIdentifier(request.getEmail());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not registered"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = jwtUtils.generateToken(user.getEmail());

        return LoginResponse.builder()
                .token(token)
                .email(user.getEmail())
                .userName(user.getUsername())
                .success(true)
                .message("Login successful")
                .build();
    }

    @Transactional
    public String resetPassword(ResetPasswordRequest request) {
        String email = normalizeIdentifier(request.getEmail());

        // 1. Verify OTP
        if (!memoryOtpService.verifyOtp(email, request.getOtp())) {
            throw new RuntimeException("Invalid or expired OTP");
        }

        // 2. Find User
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not registered"));

        // 3. Update Password
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return "Password reset successfully!";
    }
}
