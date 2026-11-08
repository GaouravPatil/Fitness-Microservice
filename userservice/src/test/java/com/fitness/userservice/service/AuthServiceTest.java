package com.fitness.userservice.service;

import com.fitness.userservice.dto.LoginRequest;
import com.fitness.userservice.dto.LoginResponse;
import com.fitness.userservice.model.User;
import com.fitness.userservice.model.UserRole;
import com.fitness.userservice.repository.UserRepository;
import com.fitness.userservice.security.JwtService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void loginReturnsTokenAndUserForValidCredentials() {
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
        when(jwtService.generateToken(user)).thenReturn(new JwtService.IssuedToken("jwt-token", 3600));

        LoginResponse response = authService.login(loginRequest(" Jane@Example.com ", "secret123"));

        assertThat(response.getAccessToken()).isEqualTo("jwt-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresInSeconds()).isEqualTo(3600);
        assertThat(response.getUser().getId()).isEqualTo("user-1");
    }

    @Test
    void loginRejectsUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest("missing@example.com", "secret123")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = User.builder().id("user-1").email("jane@example.com").password("hash").build();
        when(userRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest("jane@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
