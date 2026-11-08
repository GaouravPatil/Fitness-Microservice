package com.fitness.demo.controller;

import com.fitness.demo.dto.RecommendationResponse;
import com.fitness.demo.exception.RecommendationNotFoundException;
import com.fitness.demo.security.JwtConfig;
import com.fitness.demo.security.SecurityConfig;
import com.fitness.demo.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecommendationController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationService recommendationService;

    @Test
    void userRecommendationsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/recommendations/user/user-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userRecommendationsReturnPageForOwner() throws Exception {
        when(recommendationService.getUserRecommendations(eq("user-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response())));

        mockMvc.perform(get("/api/recommendations/user/user-1")
                        .with(jwt().jwt(token -> token.subject("user-1").claim("role", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("rec-1"))
                .andExpect(jsonPath("$.content[0].userId").value("user-1"));
    }

    @Test
    void userRecommendationsForbidOtherUsers() throws Exception {
        mockMvc.perform(get("/api/recommendations/user/user-1")
                        .with(jwt().jwt(token -> token.subject("user-2").claim("role", "USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void activityRecommendationReturnsForOwner() throws Exception {
        when(recommendationService.getActivityRecommendation("activity-1")).thenReturn(response());

        mockMvc.perform(get("/api/recommendations/activity/activity-1")
                        .with(jwt().jwt(token -> token.subject("user-1").claim("role", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendation").value("summary"));
    }

    @Test
    void activityRecommendationForbidsOtherUsers() throws Exception {
        when(recommendationService.getActivityRecommendation("activity-1")).thenReturn(response());

        mockMvc.perform(get("/api/recommendations/activity/activity-1")
                        .with(jwt().jwt(token -> token.subject("user-2").claim("role", "USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void activityRecommendationReturnsNotFound() throws Exception {
        when(recommendationService.getActivityRecommendation("missing"))
                .thenThrow(new RecommendationNotFoundException("missing"));

        mockMvc.perform(get("/api/recommendations/activity/missing")
                        .with(jwt().jwt(token -> token.subject("user-1").claim("role", "USER"))))
                .andExpect(status().isNotFound());
    }

    private RecommendationResponse response() {
        return RecommendationResponse.builder()
                .id("rec-1")
                .activityId("activity-1")
                .userId("user-1")
                .activityType("RUNNING")
                .recommendation("summary")
                .suggestions(List.of("tip"))
                .safety(List.of("safety"))
                .provider("RULES")
                .createdAt(LocalDateTime.now())
                .build();
    }
}
