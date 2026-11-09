package com.fitness.userservice.controller;

import com.fitness.userservice.dto.LoginRequest;
import com.fitness.userservice.dto.LoginResponse;
import com.fitness.userservice.dto.RefreshTokenRequest;
import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.dto.UserResponse;
import com.fitness.userservice.exception.InvalidInternalApiKeyException;
import com.fitness.userservice.service.AuthService;
import com.fitness.userservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    @Value("${app.internal.api-keys}")
    private List<String> internalApiKeys;

    public UserController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
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
        if (providedApiKey == null) {
            throw new InvalidInternalApiKeyException();
        }
        byte[] provided = providedApiKey.getBytes(StandardCharsets.UTF_8);
        boolean matches = internalApiKeys.stream()
                .filter(key -> key != null && !key.isBlank())
                .anyMatch(key -> MessageDigest.isEqual(provided, key.getBytes(StandardCharsets.UTF_8)));
        if (!matches) {
            throw new InvalidInternalApiKeyException();
        }
    }
}
