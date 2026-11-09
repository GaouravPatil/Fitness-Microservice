package com.fitness.userservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresInSeconds;
    private long refreshExpiresInSeconds;
    private UserResponse user;
}
