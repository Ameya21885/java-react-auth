package com.example.java_react_auth.module.auth.repository;

import com.example.java_react_auth.module.auth.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {
    Optional<OtpVerification> findTopByIdentifierAndOtpCodeAndIsVerifiedFalseAndExpiryTimeAfterOrderByIdDesc(
            String identifier, String otpCode, LocalDateTime now);
}
