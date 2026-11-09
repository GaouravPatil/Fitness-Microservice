package com.fitness.userservice.security;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RateLimitService {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = System.currentTimeMillis();
        long windowMillis = window.toMillis();
        AtomicBoolean allowed = new AtomicBoolean(false);

        windows.compute(key, (ignored, current) -> {
            if (current == null || now - current.startedAt >= windowMillis) {
                allowed.set(true);
                return new Window(now, 1);
            }
            if (current.count < limit) {
                allowed.set(true);
                return new Window(current.startedAt, current.count + 1);
            }
            allowed.set(false);
            return current;
        });

        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= windowMillis);
        }
        return allowed.get();
    }

    private record Window(long startedAt, int count) {
    }
}
