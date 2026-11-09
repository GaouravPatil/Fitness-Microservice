package com.fitness.userservice.security;

import com.fitness.userservice.model.User;
import com.fitness.userservice.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String ISSUER = "https://userservice.fitness.local";
    private static final String AUDIENCE = "fitness-api";
    private static final String KEY_ID = "test-key";

    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPair keyPair = generateKeyPair();
        publicKey = (RSAPublicKey) keyPair.getPublic();
        privateKey = (RSAPrivateKey) keyPair.getPrivate();
    }

    @Test
    void generatedTokenCarriesUserIdentityAndRole() {
        JwtConfig config = new JwtConfig();
        JwtEncoder encoder = config.jwtEncoder(publicKey, privateKey, KEY_ID);
        JwtDecoder decoder = config.jwtDecoder(publicKey, ISSUER, AUDIENCE);

        JwtService jwtService = new JwtService(encoder, Duration.ofHours(1), ISSUER, AUDIENCE, KEY_ID);
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
        assertThat(decoded.getIssuer().toString()).isEqualTo(ISSUER);
        assertThat(decoded.getAudience()).containsExactly(AUDIENCE);
        assertThat(decoded.getHeaders()).containsEntry("kid", KEY_ID);
        assertThat(decoded.getExpiresAt()).isAfter(Instant.now());
        assertThat(issued.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void decoderRejectsTokenSignedWithAnotherKey() throws Exception {
        JwtConfig config = new JwtConfig();
        JwtEncoder encoder = config.jwtEncoder(publicKey, privateKey, KEY_ID);

        RSAPublicKey otherPublicKey = (RSAPublicKey) generateKeyPair().getPublic();
        JwtDecoder otherDecoder = config.jwtDecoder(otherPublicKey, ISSUER, AUDIENCE);

        JwtService jwtService = new JwtService(encoder, Duration.ofMinutes(5), ISSUER, AUDIENCE, KEY_ID);
        User user = User.builder().id("user-1").email("jane@example.com").role(UserRole.USER).build();

        assertThatThrownBy(() -> otherDecoder.decode(jwtService.generateToken(user).value()))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
