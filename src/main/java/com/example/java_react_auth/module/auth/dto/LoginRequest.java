package com.example.java_react_auth.module.auth.dto;

import lombok.Data;

@Data
public class LoginRequest {
    /**
     * Email or mobile (10 digits or E.164). Preferred when both are set.
     */
    private String identifier;

    /**
     * Legacy field: treated as email or mobile when {@code identifier} is blank.
     */
    private String email;

    private String password;

    /** Raw login id from either {@code identifier} or {@code email}. */
    public String resolveLoginIdentifier() {
        if (identifier != null && !identifier.isBlank()) {
            return identifier;
        }
        return email;
    }
}
