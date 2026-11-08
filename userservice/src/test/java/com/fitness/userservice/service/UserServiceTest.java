package com.fitness.userservice.service;

import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.dto.UserResponse;
import com.fitness.userservice.exception.EmailAlreadyExistsException;
import com.fitness.userservice.exception.UserNotFoundException;
import com.fitness.userservice.model.User;
import com.fitness.userservice.model.UserRole;
import com.fitness.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void registerEncodesPasswordAndReturnsProfileWithoutPassword() {
        RegisterRequest request = registerRequest("  Jane.Doe@Example.COM ", "secret123");
        when(userRepository.existsByEmailIgnoreCase("jane.doe@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("user-1");
            user.setRole(UserRole.USER);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            return user;
        });

        UserResponse response = userService.register(request);

        assertThat(response.getId()).isEqualTo("user-1");
        assertThat(response.getEmail()).isEqualTo("jane.doe@example.com");
        assertThat(response.getFirstName()).isEqualTo("Jane");
        assertThat(response.getLastName()).isEqualTo("Doe");
        assertThat(response.getRole()).isEqualTo(UserRole.USER);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("$2a$hashed");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest("jane@example.com", "secret123")))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("jane@example.com");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerTranslatesUniqueConstraintRaceToDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> userService.register(registerRequest("jane@example.com", "secret123")))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void getUserProfileReturnsUser() {
        User user = User.builder()
                .id("user-1")
                .email("jane@example.com")
                .firstName("Jane")
                .lastName("Doe")
                .role(UserRole.USER)
                .build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UserResponse response = userService.getUserProfile("user-1");

        assertThat(response.getEmail()).isEqualTo("jane@example.com");
    }

    @Test
    void getUserProfileThrowsWhenMissing() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserProfile("missing"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void existsByUserIdDelegatesToRepository() {
        when(userRepository.existsById("user-1")).thenReturn(true);

        assertThat(userService.existsByUserId("user-1")).isTrue();
    }

    private RegisterRequest registerRequest(String email, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword(password);
        request.setFirstName(" Jane ");
        request.setLastName(" Doe ");
        return request;
    }
}
