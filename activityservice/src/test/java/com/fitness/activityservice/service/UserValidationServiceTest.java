package com.fitness.activityservice.service;

import com.fitness.activityservice.exception.UserServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import org.reactivestreams.Publisher;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserValidationServiceTest {

    private static final String INTERNAL_KEY = "test-internal-key";

    @Test
    void sendsInternalKeyAndReturnsTrueForExistingUser() {
        AtomicReference<String> receivedKey = new AtomicReference<>();
        DisposableServer server = startServer((request, response) -> {
            receivedKey.set(request.requestHeaders().get("X-Internal-Api-Key"));
            return response.status(200)
                    .header("Content-Type", "application/json")
                    .sendString(Mono.just("true"));
        });

        try {
            UserValidationService service = serviceFor(server);

            assertThat(service.validateUser("user-1")).isTrue();
            assertThat(receivedKey.get()).isEqualTo(INTERNAL_KEY);
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void returnsFalseWhenUserDoesNotExist() {
        DisposableServer server = startServer((request, response) -> response.status(200)
                .header("Content-Type", "application/json")
                .sendString(Mono.just("false")));

        try {
            assertThat(serviceFor(server).validateUser("missing")).isFalse();
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void returnsFalseWhenUserServiceReturnsNotFound() {
        DisposableServer server = startServer((request, response) -> response.status(404)
                .header("Content-Type", "application/json")
                .sendString(Mono.just("{\"message\":\"not found\"}")));

        try {
            assertThat(serviceFor(server).validateUser("missing")).isFalse();
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void throwsUnavailableWhenUserServiceReturnsServerError() {
        DisposableServer server = startServer((request, response) -> response.status(500)
                .header("Content-Type", "application/json")
                .sendString(Mono.just("{\"message\":\"boom\"}")));

        try {
            UserValidationService service = serviceFor(server);
            assertThatThrownBy(() -> service.validateUser("user-1"))
                    .isInstanceOf(UserServiceUnavailableException.class);
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void throwsUnavailableWhenUserServiceIsDown() {
        UserValidationService service = new UserValidationService(
                WebClient.builder().baseUrl("http://localhost:1").build(), INTERNAL_KEY);

        assertThatThrownBy(() -> service.validateUser("user-1"))
                .isInstanceOf(UserServiceUnavailableException.class);
    }

    private UserValidationService serviceFor(DisposableServer server) {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + server.port())
                .build();
        return new UserValidationService(webClient, INTERNAL_KEY);
    }

    private DisposableServer startServer(
            BiFunction<HttpServerRequest, HttpServerResponse, Publisher<Void>> handler) {
        return HttpServer.create()
                .host("localhost")
                .port(0)
                .handle(handler)
                .bindNow();
    }
}
