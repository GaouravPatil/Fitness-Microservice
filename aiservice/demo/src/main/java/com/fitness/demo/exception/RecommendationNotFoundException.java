package com.fitness.demo.exception;

public class RecommendationNotFoundException extends RuntimeException {

    public RecommendationNotFoundException(String activityId) {
        super("No recommendation found for activity '%s'".formatted(activityId));
    }
}
