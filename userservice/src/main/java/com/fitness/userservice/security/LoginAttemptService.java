package com.fitness.userservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration window;

    public LoginAttemptService(@Value("${app.security.login-attempts.max-attempts:5}") int maxAttempts,
                               @Value("${app.security.login-attempts.window:PT15M}") Duration window) {
        this.maxAttempts = maxAttempts;
        this.window = window;
    }

    public boolean isBlocked(String email) {
        Attempt attempt = attempts.get(email);
        if (attempt == null) {
            return false;
        }
        if (attempt.windowEndsAt.isBefore(Instant.now())) {
            attempts.remove(email, attempt);
            return false;
        }
        return attempt.failures >= maxAttempts;
    }

    public void onFailure(String email) {
        Instant now = Instant.now();
        attempts.compute(email, (ignored, attempt) -> {
            if (attempt == null || attempt.windowEndsAt.isBefore(now)) {
                return new Attempt(1, now.plus(window));
            }
            return new Attempt(attempt.failures + 1, attempt.windowEndsAt);
        });
    }

    public void onSuccess(String email) {
        attempts.remove(email);
    }

    public long retryAfterSeconds(String email) {
        Attempt attempt = attempts.get(email);
        if (attempt == null) {
            return 0;
        }
        return Math.max(0, Duration.between(Instant.now(), attempt.windowEndsAt).toSeconds());
    }

    private record Attempt(int failures, Instant windowEndsAt) {
    }
}
