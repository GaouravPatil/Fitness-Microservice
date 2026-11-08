package com.fitness.demo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.demo.event.ActivityEvent;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiRecommendationClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsEmptyWhenApiKeyIsNotConfigured() {
        OpenAiRecommendationClient client = new OpenAiRecommendationClient(
                RestClient.builder(), objectMapper, "http://localhost:1", "", "test-model");

        assertThat(client.generate(event())).isEmpty();
    }

    @Test
    void parsesStructuredAiResponse() throws IOException {
        String content = "{\"recommendation\":\"Great run\",\"suggestions\":[\"Keep pace\"],"
                + "\"safety\":[\"Drink water\"]}";
        byte[] responseBody = objectMapper.writeValueAsBytes(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", content)))));

        HttpServer server = startServer(200, responseBody);
        try {
            OpenAiRecommendationClient client = new OpenAiRecommendationClient(
                    RestClient.builder(), objectMapper,
                    "http://localhost:" + server.getAddress().getPort(), "test-key", "test-model");

            Optional<GeneratedRecommendation> result = client.generate(event());

            assertThat(result).isPresent();
            assertThat(result.get().summary()).isEqualTo("Great run");
            assertThat(result.get().suggestions()).containsExactly("Keep pace");
            assertThat(result.get().safety()).containsExactly("Drink water");
            assertThat(result.get().provider()).isEqualTo("AI");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void returnsEmptyWhenProviderReturnsMalformedJson() throws IOException {
        byte[] responseBody = objectMapper.writeValueAsBytes(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", "not-json")))));

        HttpServer server = startServer(200, responseBody);
        try {
            OpenAiRecommendationClient client = new OpenAiRecommendationClient(
                    RestClient.builder(), objectMapper,
                    "http://localhost:" + server.getAddress().getPort(), "test-key", "test-model");

            assertThat(client.generate(event())).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void returnsEmptyWhenProviderIsUnavailable() throws IOException {
        HttpServer server = startServer(500, "{}".getBytes(StandardCharsets.UTF_8));
        try {
            OpenAiRecommendationClient client = new OpenAiRecommendationClient(
                    RestClient.builder(), objectMapper,
                    "http://localhost:" + server.getAddress().getPort(), "test-key", "test-model");

            assertThat(client.generate(event())).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(int status, byte[] body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
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
