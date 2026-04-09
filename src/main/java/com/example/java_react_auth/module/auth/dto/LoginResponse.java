package com.example.java_react_auth.module.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String token;
    private String email;
    private String userName;
    private boolean success;
    private String message;
}
