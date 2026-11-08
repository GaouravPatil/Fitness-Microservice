package com.fitness.activityservice.service;

import com.fitness.activityservice.exception.UserServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Service
public class UserValidationService {

    private final WebClient userServiceWebClient;
    private final String internalApiKey;

    public UserValidationService(WebClient userServiceWebClient,
                                 @Value("${app.internal.api-key}") String internalApiKey) {
        this.userServiceWebClient = userServiceWebClient;
        this.internalApiKey = internalApiKey;
    }

    @CircuitBreaker(name = "userService", fallbackMethod = "validateUserFallback")
    public boolean validateUser(String userId) {
        log.info("Validating user {} against user service", userId);

        try {
            Boolean exists = userServiceWebClient.get()
                    .uri("/api/users/{userId}/validate", userId)
                    .header("X-Internal-Api-Key", internalApiKey)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();
            return Boolean.TRUE.equals(exists);
        } catch (WebClientResponseException.NotFound ex) {
            log.info("User {} was not found", userId);
            return false;
        } catch (WebClientException ex) {
            throw new UserServiceUnavailableException("Could not reach user service", ex);
        }
    }

    private boolean validateUserFallback(String userId, Throwable throwable) {
        log.error("User validation fallback triggered for user {}", userId, throwable);
        if (throwable instanceof UserServiceUnavailableException unavailable) {
            throw unavailable;
        }
        throw new UserServiceUnavailableException("User service is unavailable", throwable);
    }
}
