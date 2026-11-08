package com.fitness.userservice.security;

import com.fitness.userservice.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final Duration tokenTtl;

    public JwtService(JwtEncoder jwtEncoder, @Value("${app.jwt.ttl:PT1H}") Duration tokenTtl) {
        this.jwtEncoder = jwtEncoder;
        this.tokenTtl = tokenTtl;
    }

    public IssuedToken generateToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://userservice.fitness.local")
                .issuedAt(now)
                .expiresAt(now.plus(tokenTtl))
                .subject(user.getId())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, tokenTtl.toSeconds());
    }

    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
