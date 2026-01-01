package com.fitness.demo.controller;

import com.fitness.demo.model.Recommendation;
import com.fitness.demo.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    // Get all recommendations for a user
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Recommendation>> getUserRecommendations(
            @PathVariable("userId") String userId) {

        return ResponseEntity.ok(
                recommendationService.getUserRecommendation(userId)
        );
    }

    // Get recommendation for an activity
    @GetMapping("/activity/{activityId}")
    public ResponseEntity<Recommendation> getActivityRecommendation(
            @PathVariable("activityId") String activityId) {

        return ResponseEntity.ok(
                recommendationService.getActivityRecommendation(activityId)
        );
    }
}
