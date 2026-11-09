package com.fitness.userservice.controller;

import com.fitness.userservice.dto.LoginResponse;
import com.fitness.userservice.dto.UserResponse;
import com.fitness.userservice.exception.EmailAlreadyExistsException;
import com.fitness.userservice.model.UserRole;
import com.fitness.userservice.security.RateLimitService;
import com.fitness.userservice.security.SecurityConfig;
import com.fitness.userservice.service.AuthService;
import com.fitness.userservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.internal.api-keys=test-internal-key")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private RateLimitService rateLimitService;

    @BeforeEach
    void allowRequestsByDefault() {
        when(rateLimitService.tryAcquire(any(), any(Integer.class), any())).thenReturn(true);
    }

    @Test
    void registerReturnsCreatedUserWithoutPassword() throws Exception {
        when(userService.register(any())).thenReturn(userResponse("user-1"));

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane@example.com","password":"secret123",
                                 "firstName":"Jane","lastName":"Doe"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("user-1"))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void registerRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"123","firstName":"","lastName":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.firstName").exists())
                .andExpect(jsonPath("$.fieldErrors.lastName").exists());
    }

    @Test
    void registerReturnsConflictForDuplicateEmail() throws Exception {
        when(userService.register(any())).thenThrow(new EmailAlreadyExistsException("jane@example.com"));

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane@example.com","password":"secret123",
                                 "firstName":"Jane","lastName":"Doe"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with email 'jane@example.com' already exists"));
    }

    @Test
    void loginReturnsToken() throws Exception {
        when(authService.login(any())).thenReturn(LoginResponse.builder()
                .accessToken("jwt-token")
                .tokenType("Bearer")
                .expiresInSeconds(3600)
                .user(userResponse("user-1"))
                .build());

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane@example.com","password":"secret123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void loginReturnsUnauthorizedForBadCredentials() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("bad credentials"));

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane@example.com","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/user-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profileAllowsOwner() throws Exception {
        when(userService.getUserProfile("user-1")).thenReturn(userResponse("user-1"));

        mockMvc.perform(get("/api/users/user-1")
                        .with(jwt().jwt(token -> token.subject("user-1").claim("role", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-1"));
    }

    @Test
    void profileForbidsOtherUsers() throws Exception {
        mockMvc.perform(get("/api/users/user-1")
                        .with(jwt().jwt(token -> token.subject("user-2").claim("role", "USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadAnyProfile() throws Exception {
        when(userService.getUserProfile("user-1")).thenReturn(userResponse("user-1"));

        mockMvc.perform(get("/api/users/user-1")
                        .with(jwt().jwt(token -> token.subject("admin").claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void validateRequiresInternalApiKey() throws Exception {
        mockMvc.perform(get("/api/users/user-1/validate"))
                .andExpect(status().isForbidden());
    }

    @Test
    void validateReturnsExistenceWithInternalApiKey() throws Exception {
        when(userService.existsByUserId("user-1")).thenReturn(true);

        mockMvc.perform(get("/api/users/user-1/validate")
                        .header("X-Internal-Api-Key", "test-internal-key"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    private UserResponse userResponse(String id) {
        return UserResponse.builder()
                .id(id)
                .email("jane@example.com")
                .firstName("Jane")
                .lastName("Doe")
                .role(UserRole.USER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
