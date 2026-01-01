package com.fitness.activityservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserValidationService {

    private final WebClient userServiceWebClient;

    // Method 1: Validate user
    public boolean validateUser(String userId) {
        log.info("Calling UserService for validation of userID: {}", userId);

        try {
            return Boolean.TRUE.equals(
                    userServiceWebClient.get()
                            .uri("/api/user/{userId}/validate", userId)
                            .retrieve()
                            .bodyToMono(Boolean.class)
                            .block()
            );
        } catch (WebClientResponseException e) {
            log.error("Error while validating user {}: {}", userId, e.getMessage());
        }
        return false;
    }

    // Method 2: Check if user exists
    public Boolean existByUserId(String userId) {
        log.info("Calling UserService to check existence for userID: {}", userId);

        try {
            return userServiceWebClient.get()
                    .uri("/api/user/{userId}/exists", userId)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Error while checking user existence {}: {}", userId, e.getMessage());
            return false;
        }
    }
}
