package com.fitness.userservice.service;

import com.fitness.userservice.dto.LoginRequest;
import com.fitness.userservice.dto.LoginResponse;
import com.fitness.userservice.exception.TooManyLoginAttemptsException;
import com.fitness.userservice.model.User;
import com.fitness.userservice.model.UserRole;
import com.fitness.userservice.repository.UserRepository;
import com.fitness.userservice.security.JwtService;
import com.fitness.userservice.security.LoginAttemptService;
import com.fitness.userservice.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private LoginAttemptService loginAttemptService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService,
                loginAttemptService);
    }

    @Test
    void loginReturnsAccessAndRefreshTokensForValidCredentials() {
        User user = User.builder()
                .id("user-1")
                .email("jane@example.com")
                .password("hash")
                .firstName("Jane")
                .lastName("Doe")
                .role(UserRole.USER)
                .build();
        when(userRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn(new JwtService.IssuedToken("jwt-token", 900));
        when(refreshTokenService.issue("user-1"))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh-token", 604800));

        LoginResponse response = authService.login(loginRequest(" Jane@Example.com ", "secret123"));

        assertThat(response.getAccessToken()).isEqualTo("jwt-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresInSeconds()).isEqualTo(900);
        assertThat(response.getRefreshExpiresInSeconds()).isEqualTo(604800);
        assertThat(response.getUser().getId()).isEqualTo("user-1");
        verify(loginAttemptService).onSuccess("jane@example.com");
    }

    @Test
    void loginRejectsUnknownEmailAndRecordsFailure() {
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest("missing@example.com", "secret123")))
                .isInstanceOf(BadCredentialsException.class);

        verify(loginAttemptService).onFailure("missing@example.com");
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = User.builder().id("user-1").email("jane@example.com").password("hash").build();
        when(userRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest("jane@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);

        verify(loginAttemptService).onFailure("jane@example.com");
    }

    @Test
    void loginRejectsBlockedAccountWithRetryAfter() {
        when(loginAttemptService.isBlocked("jane@example.com")).thenReturn(true);
        when(loginAttemptService.retryAfterSeconds("jane@example.com")).thenReturn(120L);

        assertThatThrownBy(() -> authService.login(loginRequest("jane@example.com", "secret123")))
                .isInstanceOf(TooManyLoginAttemptsException.class)
                .hasMessageContaining("Too many failed login attempts");
    }

    @Test
    void refreshRotatesTokenAndIssuesNewAccessToken() {
        User user = User.builder()
                .id("user-1")
                .email("jane@example.com")
                .firstName("Jane")
                .lastName("Doe")
                .role(UserRole.USER)
                .build();
        when(refreshTokenService.rotate("refresh-token"))
                .thenReturn(new RefreshTokenService.RotationResult("user-1", "refresh-token-2", 604800));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn(new JwtService.IssuedToken("access-2", 900));

        LoginResponse response = authService.refresh("refresh-token");

        assertThat(response.getAccessToken()).isEqualTo("access-2");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token-2");
        assertThat(response.getExpiresInSeconds()).isEqualTo(900);
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
