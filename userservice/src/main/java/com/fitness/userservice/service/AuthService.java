package com.fitness.userservice.service;

import com.fitness.userservice.dto.LoginRequest;
import com.fitness.userservice.dto.LoginResponse;
import com.fitness.userservice.exception.InvalidRefreshTokenException;
import com.fitness.userservice.exception.TooManyLoginAttemptsException;
import com.fitness.userservice.model.User;
import com.fitness.userservice.repository.UserRepository;
import com.fitness.userservice.security.JwtService;
import com.fitness.userservice.security.LoginAttemptService;
import com.fitness.userservice.security.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = UserService.normalizeEmail(request.getEmail());

        if (loginAttemptService.isBlocked(email)) {
            throw new TooManyLoginAttemptsException(loginAttemptService.retryAfterSeconds(email));
        }

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginAttemptService.onFailure(email);
            throw new BadCredentialsException("Invalid email or password");
        }

        loginAttemptService.onSuccess(email);
        return issueTokens(user);
    }

    @Transactional
    public LoginResponse refresh(String rawRefreshToken) {
        RefreshTokenService.RotationResult rotated = refreshTokenService.rotate(rawRefreshToken);
        User user = userRepository.findById(rotated.userId())
                .orElseThrow(InvalidRefreshTokenException::new);
        JwtService.IssuedToken accessToken = jwtService.generateToken(user);

        return LoginResponse.builder()
                .accessToken(accessToken.value())
                .refreshToken(rotated.refreshToken())
                .tokenType("Bearer")
                .expiresInSeconds(accessToken.expiresInSeconds())
                .refreshExpiresInSeconds(rotated.expiresInSeconds())
                .user(UserService.toResponse(user))
                .build();
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private LoginResponse issueTokens(User user) {
        JwtService.IssuedToken accessToken = jwtService.generateToken(user);
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user.getId());

        return LoginResponse.builder()
                .accessToken(accessToken.value())
                .refreshToken(refreshToken.value())
                .tokenType("Bearer")
                .expiresInSeconds(accessToken.expiresInSeconds())
                .refreshExpiresInSeconds(refreshToken.expiresInSeconds())
                .user(UserService.toResponse(user))
                .build();
    }
}
