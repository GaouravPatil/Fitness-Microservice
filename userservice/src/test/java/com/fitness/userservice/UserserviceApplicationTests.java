package com.fitness.userservice;

import com.fitness.userservice.dto.LoginRequest;
import com.fitness.userservice.dto.LoginResponse;
import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.model.RefreshToken;
import com.fitness.userservice.repository.RefreshTokenRepository;
import com.fitness.userservice.service.AuthService;
import com.fitness.userservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UserserviceApplicationTests {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void contextLoads() {
    }

    @Test
    void loginPersistsOnlyAHashedRefreshToken() {
        RegisterRequest register = new RegisterRequest();
        register.setEmail("persist.check@example.com");
        register.setPassword("PersistCheck123!");
        register.setFirstName("Persist");
        register.setLastName("Check");
        String userId = userService.register(register).getId();

        LoginRequest login = new LoginRequest();
        login.setEmail("persist.check@example.com");
        login.setPassword("PersistCheck123!");
        LoginResponse response = authService.login(login);

        List<RefreshToken> stored = refreshTokenRepository.findAll().stream()
                .filter(token -> token.getUserId().equals(userId))
                .toList();

        assertThat(stored).hasSize(1);
        RefreshToken token = stored.getFirst();
        assertThat(token.getTokenHash()).hasSize(64);
        assertThat(token.getTokenHash()).isNotEqualTo(response.getRefreshToken());
        assertThat(token.isRevoked()).isFalse();
        assertThat(token.getExpiresAt()).isAfter(java.time.LocalDateTime.now());
    }
}
