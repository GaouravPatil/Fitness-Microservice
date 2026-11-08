package com.fitness.userservice.service;

import com.fitness.userservice.dto.LoginRequest;
import com.fitness.userservice.dto.LoginResponse;
import com.fitness.userservice.model.User;
import com.fitness.userservice.repository.UserRepository;
import com.fitness.userservice.security.JwtService;
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

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(UserService.normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        JwtService.IssuedToken token = jwtService.generateToken(user);
        return LoginResponse.builder()
                .accessToken(token.value())
                .tokenType("Bearer")
                .expiresInSeconds(token.expiresInSeconds())
                .user(UserService.toResponse(user))
                .build();
    }
}
