package com.fitness.userservice.security;

import com.fitness.userservice.model.User;
import com.fitness.userservice.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "c4L9vfsY0NFwBrbCooZlKgYHCFvcLPZgwTTFXpUA3y0=";

    @Test
    void generatedTokenCarriesUserIdentityAndRole() {
        JwtConfig config = new JwtConfig();
        SecretKey key = config.jwtSecretKey(SECRET);
        JwtEncoder encoder = config.jwtEncoder(key);
        JwtDecoder decoder = config.jwtDecoder(key);

        JwtService jwtService = new JwtService(encoder, Duration.ofHours(1));
        User user = User.builder()
                .id("user-1")
                .email("jane@example.com")
                .role(UserRole.ADMIN)
                .build();

        JwtService.IssuedToken issued = jwtService.generateToken(user);
        Jwt decoded = decoder.decode(issued.value());

        assertThat(decoded.getSubject()).isEqualTo("user-1");
        assertThat(decoded.getClaimAsString("email")).isEqualTo("jane@example.com");
        assertThat(decoded.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(decoded.getIssuer().toString()).isEqualTo("https://userservice.fitness.local");
        assertThat(decoded.getExpiresAt()).isAfter(Instant.now());
        assertThat(issued.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void decoderRejectsTokenSignedWithAnotherKey() {
        JwtConfig config = new JwtConfig();
        SecretKey key = config.jwtSecretKey(SECRET);
        JwtEncoder encoder = config.jwtEncoder(key);

        SecretKey otherKey = config.jwtSecretKey("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        JwtDecoder otherDecoder = config.jwtDecoder(otherKey);

        JwtService jwtService = new JwtService(encoder, Duration.ofMinutes(5));
        User user = User.builder().id("user-1").email("jane@example.com").role(UserRole.USER).build();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> otherDecoder.decode(jwtService.generateToken(user).value()))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }
}
