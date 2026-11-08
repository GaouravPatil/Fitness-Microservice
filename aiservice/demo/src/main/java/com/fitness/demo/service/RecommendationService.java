package com.fitness.demo.service;

import com.fitness.demo.dto.RecommendationResponse;
import com.fitness.demo.event.ActivityEvent;
import com.fitness.demo.exception.RecommendationNotFoundException;
import com.fitness.demo.model.Recommendation;
import com.fitness.demo.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationRepository recommendationRepository;
    private final RecommendationGenerator recommendationGenerator;

    public Page<RecommendationResponse> getUserRecommendations(String userId, Pageable pageable) {
        return recommendationRepository.findByUserId(userId, pageable)
                .map(RecommendationService::toResponse);
    }

    public RecommendationResponse getActivityRecommendation(String activityId) {
        Recommendation recommendation = recommendationRepository
                .findFirstByActivityIdOrderByCreatedAtDesc(activityId)
                .orElseThrow(() -> new RecommendationNotFoundException(activityId));
        return toResponse(recommendation);
    }

    public RecommendationResponse generateRecommendation(ActivityEvent event) {
        GeneratedRecommendation generated = recommendationGenerator.generate(event);

        Recommendation recommendation = recommendationRepository
                .findFirstByActivityIdOrderByCreatedAtDesc(event.getId())
                .orElseGet(Recommendation::new);

        recommendation.setActivityId(event.getId());
        recommendation.setUserId(event.getUserId());
        recommendation.setActivityType(event.getType());
        recommendation.setRecommendation(generated.summary());
        recommendation.setSuggestions(generated.suggestions());
        recommendation.setSafety(generated.safety());
        recommendation.setProvider(generated.provider());

        Recommendation saved = recommendationRepository.save(recommendation);
        log.info("Stored recommendation {} for activity {} using {}", saved.getId(), event.getId(),
                generated.provider());
        return toResponse(saved);
    }

    private static RecommendationResponse toResponse(Recommendation recommendation) {
        return RecommendationResponse.builder()
                .id(recommendation.getId())
                .activityId(recommendation.getActivityId())
                .userId(recommendation.getUserId())
                .activityType(recommendation.getActivityType())
                .recommendation(recommendation.getRecommendation())
                .suggestions(recommendation.getSuggestions())
                .safety(recommendation.getSafety())
                .provider(recommendation.getProvider())
                .createdAt(recommendation.getCreatedAt())
                .build();
    }
}
