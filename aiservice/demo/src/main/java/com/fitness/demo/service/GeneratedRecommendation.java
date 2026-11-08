package com.fitness.demo.service;

import java.util.List;

public record GeneratedRecommendation(String summary, List<String> suggestions, List<String> safety,
                                      String provider) {
}
