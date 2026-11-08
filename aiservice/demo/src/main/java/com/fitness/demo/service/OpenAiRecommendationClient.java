package com.fitness.demo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.demo.event.ActivityEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class OpenAiRecommendationClient {

    private static final String SYSTEM_PROMPT = """
            You are a certified fitness coach. Given one workout, reply only with a JSON object using this schema:
            {"recommendation": "one short paragraph", "suggestions": ["actionable tip"], "safety": ["safety tip"]}
            Keep suggestions and safety lists short (2-4 items each). Do not include markdown.
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public OpenAiRecommendationClient(RestClient.Builder restClientBuilder,
                                      ObjectMapper objectMapper,
                                      @Value("${app.ai.base-url:https://api.openai.com/v1}") String baseUrl,
                                      @Value("${app.ai.api-key:}") String apiKey,
                                      @Value("${app.ai.model:gpt-4o-mini}") String model) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(10000);

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    public Optional<GeneratedRecommendation> generate(ActivityEvent event) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "temperature", 0.4,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", describe(event))));

            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                return Optional.empty();
            }

            String content = response.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                return Optional.empty();
            }

            JsonNode payload = objectMapper.readTree(content);
            String summary = payload.path("recommendation").asText("");
            if (summary.isBlank()) {
                return Optional.empty();
            }

            List<String> suggestions = objectMapper.convertValue(payload.path("suggestions"),
                    new TypeReference<List<String>>() {
                    });
            List<String> safety = objectMapper.convertValue(payload.path("safety"),
                    new TypeReference<List<String>>() {
                    });

            return Optional.of(new GeneratedRecommendation(summary, suggestions, safety, "AI"));
        } catch (Exception ex) {
            log.warn("AI recommendation provider failed, falling back to rule-based advice: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private String describe(ActivityEvent event) {
        return "Activity: %s, duration: %s minutes, calories burned: %s, started at: %s, metrics: %s"
                .formatted(event.getType(), event.getDuration(), event.getCaloriesBurned(),
                        event.getStartTime(), event.getAdditionalMetrics());
    }
}
