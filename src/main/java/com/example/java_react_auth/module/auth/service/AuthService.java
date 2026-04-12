package com.example.java_react_auth.module.auth.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final MemoryOtpService memoryOtpService;
    private final List<NotificationService> notificationServices;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    /**
     * Normalizes login / OTP identifiers: emails are trimmed and lower-cased; phones have non-digits
     * stripped, 10-digit Indian mobiles get +91, 12-digit numbers starting with 91 become +91….
     */
    private String normalizeIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        String trimmed = identifier.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        if (trimmed.contains("@")) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        String digits = trimmed.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return trimmed;
        }
        if (digits.length() == 10) {
            return "+91" + digits;
        }
        if (digits.length() >= 12 && digits.startsWith("91")) {
            return "+" + digits;
        }
        return "+" + digits;
    }

    private Optional<User> findRegisteredUserByIdentifier(String normalizedId) {
        if (normalizedId == null || normalizedId.isBlank()) {
            return Optional.empty();
        }
        if (normalizedId.contains("@")) {
            return userRepository.findByEmailIgnoreCase(normalizedId);
        }
        return userRepository.findByPhoneNumber(normalizedId);
    }

    private String generateOtpCode() {
        return String.format("%06d", new Random().nextInt(1000000));
    }

    private void logOtpToConsole(String normalizedId, String otp) {
        System.out.println("========================================");
        System.out.println("OTP for " + normalizedId + " is: " + otp);
        System.out.println("========================================");
    }

    /** Sends OTP on a channel; does not persist OTP (caller handles storage). */
    private String trySendOtpNotification(String normalizedId, String otp) {
        try {
            NotificationService service = notificationServices.stream()
                    .filter(s -> s.supports(normalizedId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException(
                            "No notification channel available for: " + normalizedId
                                    + ". For email set MAIL_PASSWORD; for WhatsApp set TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN,"
                                    + " and join the Twilio sandbox from the recipient phone if using the sandbox number."));
            service.sendOtp(normalizedId, otp);
            return "OTP sent successfully to " + normalizedId;
        } catch (Exception e) {
            log.warn("Failed to send OTP notification to {}: {}", normalizedId, e.toString(), e);
            return "OTP generated and logged to console, but notification failed for " + normalizedId + ": "
                    + e.getMessage();
        }
    }

    /**
     * Persists OTP and attempts delivery for one channel (email or phone).
     *
     * @return user-facing status for this channel
     */
    private String deliverOtpToChannel(String normalizedId, String otp) {
        memoryOtpService.saveOtp(normalizedId, otp);
        logOtpToConsole(normalizedId, otp);
        return trySendOtpNotification(normalizedId, otp);
    }

    @Transactional
    public String sendOtp(String identifier) {
        final String normalizedId = normalizeIdentifier(identifier);
        if (findRegisteredUserByIdentifier(normalizedId).isEmpty()) {
            throw new RuntimeException("User not registered with identifier: " + normalizedId);
        }

        String otp = generateOtpCode();
        return deliverOtpToChannel(normalizedId, otp);
    }

    /**
     * Sends OTP to an email during sign-up. Does not require an existing account.
     * If the email is already registered, refuses (avoids spamming existing users).
     */
    @Transactional
    public String sendRegistrationOtpForEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Email is required");
        }
        String normalized = normalizeIdentifier(email);
        if (!normalized.contains("@")) {
            throw new RuntimeException("Invalid email address");
        }
        if (userRepository.findByEmailIgnoreCase(normalized).isPresent()) {
            throw new RuntimeException("This email is already registered");
        }
        String otp = generateOtpCode();
        return deliverOtpToChannel(normalized, otp);
    }

    /**
     * Sends OTP to a phone during sign-up. Does not require an existing account.
     * If the number is already registered, refuses.
     */
    @Transactional
    public String sendRegistrationOtpForPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new RuntimeException("Phone number is required");
        }
        String normalized = normalizeIdentifier(phone);
        if (normalized.contains("@")) {
            throw new RuntimeException("Invalid phone number");
        }
        if (userRepository.findByPhoneNumber(normalized).isPresent()) {
            throw new RuntimeException("This phone number is already registered");
        }
        String otp = generateOtpCode();
        return deliverOtpToChannel(normalized, otp);
    }

    public boolean verifyOtp(String identifier, String otp) {
        identifier = normalizeIdentifier(identifier);
        return memoryOtpService.verifyOtp(identifier, otp);
    }

    @Transactional
    public String register(RegistrationRequest request) {
        String email = normalizeIdentifier(request.getEmail());
        String phoneNumber = normalizeIdentifier(request.getPhoneNumber());

        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
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

        String otp = generateOtpCode();
        memoryOtpService.saveOtp(email, otp);
        memoryOtpService.saveOtp(phoneNumber, otp);
        System.out.println("========================================");
        System.out.println("Registration OTP (same code on email & phone) for " + email + " / " + phoneNumber
                + " is: " + otp);
        System.out.println("========================================");

        String emailStatus = trySendOtpNotification(email, otp);
        String phoneStatus = trySendOtpNotification(phoneNumber, otp);

        return "Account created successfully! " + emailStatus + " " + phoneStatus;
    }

    public LoginResponse login(LoginRequest request) {
        String raw = request.resolveLoginIdentifier();
        if (raw == null || raw.isBlank()) {
            throw new RuntimeException("Email or mobile is required");
        }
        String normalizedId = normalizeIdentifier(raw);
        User user = findRegisteredUserByIdentifier(normalizedId)
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
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RuntimeException("User not registered"));

        // 3. Update Password
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return "Password reset successfully!";
    }
}
