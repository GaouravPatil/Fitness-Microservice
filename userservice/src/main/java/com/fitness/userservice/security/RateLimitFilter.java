package com.fitness.userservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    private final int loginLimit;
    private final Duration loginWindow;
    private final int registerLimit;
    private final Duration registerWindow;

    public RateLimitFilter(RateLimitService rateLimitService,
                           ObjectMapper objectMapper,
                           @Value("${app.security.rate-limit.login-limit:10}") int loginLimit,
                           @Value("${app.security.rate-limit.login-window:PT1M}") Duration loginWindow,
                           @Value("${app.security.rate-limit.register-limit:5}") int registerLimit,
                           @Value("${app.security.rate-limit.register-window:PT1H}") Duration registerWindow) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
        this.loginLimit = loginLimit;
        this.loginWindow = loginWindow;
        this.registerLimit = registerLimit;
        this.registerWindow = registerWindow;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (HttpMethod.POST.matches(request.getMethod())) {
            String path = request.getRequestURI();
            String bucket = null;
            int limit = 0;
            Duration window = Duration.ZERO;

            if ("/api/users/login".equals(path)) {
                bucket = "login:" + clientIp(request);
                limit = loginLimit;
                window = loginWindow;
            } else if ("/api/users/register".equals(path)) {
                bucket = "register:" + clientIp(request);
                limit = registerLimit;
                window = registerWindow;
            }

            if (bucket != null && !rateLimitService.tryAcquire(bucket, limit, window)) {
                response.setStatus(429);
                response.setHeader("Retry-After", Long.toString(Math.max(1, window.toSeconds())));
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(response.getOutputStream(), Map.of(
                        "timestamp", Instant.now().toString(),
                        "status", 429,
                        "error", "Too Many Requests",
                        "message", "Too many requests, please try again later",
                        "path", path));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
