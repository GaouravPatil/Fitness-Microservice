package com.fitness.activityservice.controller;

import com.fitness.activityservice.dto.ActivityRequest;
import com.fitness.activityservice.dto.ActivityResponse;
import com.fitness.activityservice.service.ActivityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @PostMapping
    public ResponseEntity<ActivityResponse> trackActivity(@Valid @RequestBody ActivityRequest request,
                                                          JwtAuthenticationToken authentication) {
        requireSelfOrAdmin(authentication, request.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(activityService.trackActivity(request));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<ActivityResponse>> getUserActivities(
            @PathVariable String userId,
            @PageableDefault(size = 20) Pageable pageable,
            JwtAuthenticationToken authentication) {
        requireSelfOrAdmin(authentication, userId);
        return ResponseEntity.ok(activityService.getActivities(userId, pageable));
    }

    private void requireSelfOrAdmin(JwtAuthenticationToken authentication, String userId) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!isAdmin && !userId.equals(authentication.getToken().getSubject())) {
            throw new AccessDeniedException("You can only manage your own activities");
        }
    }
}
