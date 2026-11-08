package com.fitness.demo.service;

import com.fitness.demo.event.ActivityEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationGeneratorTest {

    @Mock
    private OpenAiRecommendationClient openAiRecommendationClient;

    private RecommendationGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new RecommendationGenerator(openAiRecommendationClient);
    }

    @Test
    void usesAiResultWhenAvailable() {
        GeneratedRecommendation aiResult = new GeneratedRecommendation(
                "AI summary", List.of("AI suggestion"), List.of("AI safety"), "AI");
        when(openAiRecommendationClient.generate(any(ActivityEvent.class))).thenReturn(Optional.of(aiResult));

        GeneratedRecommendation result = generator.generate(event("RUNNING", 30, 300));

        assertThat(result).isEqualTo(aiResult);
    }

    @Test
    void fallsBackToRulesWhenAiIsNotConfigured() {
        when(openAiRecommendationClient.generate(any(ActivityEvent.class))).thenReturn(Optional.empty());

        GeneratedRecommendation result = generator.generate(event("RUNNING", 30, 300));

        assertThat(result.provider()).isEqualTo("RULES");
        assertThat(result.summary()).contains("30 minutes", "running", "300 calories");
        assertThat(result.suggestions()).anyMatch(s -> s.contains("interval"));
        assertThat(result.safety()).isNotEmpty();
    }

    @Test
    void givesStrengthSpecificAdvice() {
        when(openAiRecommendationClient.generate(any(ActivityEvent.class))).thenReturn(Optional.empty());

        GeneratedRecommendation result = generator.generate(event("WEIGHT_TRAINING", 45, 250));

        assertThat(result.suggestions()).anyMatch(s -> s.contains("progressive overload"));
    }

    @Test
    void addsRecoveryAdviceForLongHighCalorieSessions() {
        when(openAiRecommendationClient.generate(any(ActivityEvent.class))).thenReturn(Optional.empty());

        GeneratedRecommendation result = generator.generate(event("CYCLING", 180, 1500));

        assertThat(result.safety()).anyMatch(s -> s.contains("long session"));
        assertThat(result.safety()).anyMatch(s -> s.contains("electrolytes"));
    }

    private ActivityEvent event(String type, int duration, int calories) {
        return ActivityEvent.builder()
                .id("activity-1")
                .userId("user-1")
                .type(type)
                .duration(duration)
                .caloriesBurned(calories)
                .startTime(LocalDateTime.of(2026, 1, 1, 10, 0))
                .build();
    }
}
