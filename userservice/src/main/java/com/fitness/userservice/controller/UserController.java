package com.fitness.userservice.controller;

import com.fitness.userservice.dto.LoginRequest;
import com.fitness.userservice.dto.LoginResponse;
import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.dto.UserResponse;
import com.fitness.userservice.exception.InvalidInternalApiKeyException;
import com.fitness.userservice.service.AuthService;
import com.fitness.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    @Value("${app.internal.api-key}")
    private String internalApiKey;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserProfile(@PathVariable String userId,
                                                       JwtAuthenticationToken authentication) {
        requireSelfOrAdmin(authentication, userId);
        return ResponseEntity.ok(userService.getUserProfile(userId));
    }

    @GetMapping("/{userId}/validate")
    public ResponseEntity<Boolean> validateUser(@PathVariable String userId,
                                                @RequestHeader(value = "X-Internal-Api-Key", required = false)
                                                String providedApiKey) {
        requireValidInternalKey(providedApiKey);
        return ResponseEntity.ok(userService.existsByUserId(userId));
    }

    private void requireSelfOrAdmin(JwtAuthenticationToken authentication, String userId) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!isAdmin && !userId.equals(authentication.getToken().getSubject())) {
            throw new AccessDeniedException("You can only access your own profile");
        }
    }

    private void requireValidInternalKey(String providedApiKey) {
        if (providedApiKey == null || internalApiKey == null
                || !MessageDigest.isEqual(providedApiKey.getBytes(StandardCharsets.UTF_8),
                internalApiKey.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidInternalApiKeyException();
        }
    }
}
