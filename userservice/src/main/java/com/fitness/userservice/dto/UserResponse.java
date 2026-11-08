package com.fitness.userservice.dto;

import com.fitness.userservice.model.UserRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {

    private String id;
    private String email;
    private String firstName;
    private String lastName;
    private UserRole role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
