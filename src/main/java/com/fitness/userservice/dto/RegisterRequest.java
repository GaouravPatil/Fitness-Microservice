package com.fitness.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data

public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message  = "Invalid email format")

    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6 , message = "password must have 6 characters")
    private String password;
    private String f_name;
    private String l_name;

}
