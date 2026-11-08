package com.fitness.demo.service;

import com.fitness.demo.dto.RecommendationResponse;
import com.fitness.demo.event.ActivityEvent;
import com.fitness.demo.exception.RecommendationNotFoundException;
import com.fitness.demo.model.Recommendation;
import com.fitness.demo.repository.RecommendationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private RecommendationRepository recommendationRepository;

    @Mock
    private RecommendationGenerator recommendationGenerator;

    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(recommendationRepository, recommendationGenerator);
    }

    @Test
    void getUserRecommendationsMapsPage() {
        Pageable pageable = PageRequest.of(0, 20);
        Recommendation recommendation = recommendation("rec-1", "activity-1");
        when(recommendationRepository.findByUserId("user-1", pageable))
                .thenReturn(new PageImpl<>(List.of(recommendation), pageable, 1));

        Page<RecommendationResponse> page = recommendationService.getUserRecommendations("user-1", pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().getId()).isEqualTo("rec-1");
        assertThat(page.getContent().getFirst().getUserId()).isEqualTo("user-1");
    }

    @Test
    void getActivityRecommendationThrowsWhenMissing() {
        when(recommendationRepository.findFirstByActivityIdOrderByCreatedAtDesc("activity-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> recommendationService.getActivityRecommendation("activity-1"))
                .isInstanceOf(RecommendationNotFoundException.class);
    }

    @Test
    void generatesAndStoresNewRecommendation() {
        ActivityEvent event = event();
        when(recommendationRepository.findFirstByActivityIdOrderByCreatedAtDesc("activity-1"))
                .thenReturn(Optional.empty());
        when(recommendationGenerator.generate(event))
                .thenReturn(new GeneratedRecommendation("summary", List.of("tip"), List.of("safety"), "RULES"));
        when(recommendationRepository.save(any(Recommendation.class))).thenAnswer(invocation -> {
            Recommendation recommendation = invocation.getArgument(0);
            recommendation.setId("rec-1");
            recommendation.setCreatedAt(LocalDateTime.now());
            return recommendation;
        });

        RecommendationResponse response = recommendationService.generateRecommendation(event);

        assertThat(response.getId()).isEqualTo("rec-1");
        assertThat(response.getActivityId()).isEqualTo("activity-1");
        assertThat(response.getRecommendation()).isEqualTo("summary");
        assertThat(response.getProvider()).isEqualTo("RULES");
    }

    @Test
    void updatingExistingRecommendationKeepsDocumentId() {
        Recommendation existing = recommendation("rec-1", "activity-1");
        when(recommendationRepository.findFirstByActivityIdOrderByCreatedAtDesc("activity-1"))
                .thenReturn(Optional.of(existing));
        when(recommendationGenerator.generate(any()))
                .thenReturn(new GeneratedRecommendation("new summary", List.of("tip"), List.of("safety"), "AI"));
        when(recommendationRepository.save(any(Recommendation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecommendationResponse response = recommendationService.generateRecommendation(event());

        assertThat(response.getId()).isEqualTo("rec-1");
        assertThat(response.getRecommendation()).isEqualTo("new summary");
        assertThat(response.getProvider()).isEqualTo("AI");
    }

    private Recommendation recommendation(String id, String activityId) {
        return Recommendation.builder()
                .id(id)
                .activityId(activityId)
                .userId("user-1")
                .activityType("RUNNING")
                .recommendation("summary")
                .suggestions(List.of("tip"))
                .safety(List.of("safety"))
                .provider("RULES")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ActivityEvent event() {
        return ActivityEvent.builder()
                .id("activity-1")
                .userId("user-1")
                .type("RUNNING")
                .duration(30)
                .caloriesBurned(300)
                .startTime(LocalDateTime.of(2026, 1, 1, 10, 0))
                .build();
    }
}
