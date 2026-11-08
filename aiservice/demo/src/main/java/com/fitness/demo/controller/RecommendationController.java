package com.fitness.demo.controller;

import com.fitness.demo.dto.RecommendationResponse;
import com.fitness.demo.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<RecommendationResponse>> getUserRecommendations(
            @PathVariable String userId,
            @PageableDefault(size = 20) Pageable pageable,
            JwtAuthenticationToken authentication) {
        requireSelfOrAdmin(authentication, userId);
        return ResponseEntity.ok(recommendationService.getUserRecommendations(userId, pageable));
    }

    @GetMapping("/activity/{activityId}")
    public ResponseEntity<RecommendationResponse> getActivityRecommendation(
            @PathVariable String activityId,
            JwtAuthenticationToken authentication) {
        RecommendationResponse response = recommendationService.getActivityRecommendation(activityId);
        requireSelfOrAdmin(authentication, response.getUserId());
        return ResponseEntity.ok(response);
    }

    private void requireSelfOrAdmin(JwtAuthenticationToken authentication, String userId) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!isAdmin && !userId.equals(authentication.getToken().getSubject())) {
            throw new AccessDeniedException("You can only access your own recommendations");
        }
    }
}
