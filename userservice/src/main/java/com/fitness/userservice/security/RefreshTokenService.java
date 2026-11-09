package com.fitness.userservice.security;

import com.fitness.userservice.exception.InvalidRefreshTokenException;
import com.fitness.userservice.model.RefreshToken;
import com.fitness.userservice.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration refreshTtl;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               @Value("${app.jwt.refresh-ttl:P7D}") Duration refreshTtl) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTtl = refreshTtl;
    }

    @Transactional
    public IssuedRefreshToken issue(String userId) {
        String rawToken = newToken();
        RefreshToken token = RefreshToken.builder()
                .tokenHash(hash(rawToken))
                .userId(userId)
                .expiresAt(LocalDateTime.now().plus(refreshTtl))
                .build();
        refreshTokenRepository.save(token);
        return new IssuedRefreshToken(rawToken, refreshTtl.toSeconds());
    }

    @Transactional
    public RotationResult rotate(String rawToken) {
        RefreshToken token = requireActive(rawToken);
        token.setRevoked(true);
        refreshTokenRepository.save(token);
        IssuedRefreshToken next = issue(token.getUserId());
        return new RotationResult(token.getUserId(), next.value(), next.expiresInSeconds());
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
            }
        });
    }

    private RefreshToken requireActive(String rawToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (token.isRevoked() || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidRefreshTokenException();
        }
        return token;
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public record IssuedRefreshToken(String value, long expiresInSeconds) {
    }

    public record RotationResult(String userId, String refreshToken, long expiresInSeconds) {
    }
}
