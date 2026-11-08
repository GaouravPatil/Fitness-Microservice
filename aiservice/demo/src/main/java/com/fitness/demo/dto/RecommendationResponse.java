package com.fitness.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RecommendationResponse {

    private String id;
    private String activityId;
    private String userId;
    private String activityType;
    private String recommendation;
    private List<String> suggestions;
    private List<String> safety;
    private String provider;
    private LocalDateTime createdAt;
}
