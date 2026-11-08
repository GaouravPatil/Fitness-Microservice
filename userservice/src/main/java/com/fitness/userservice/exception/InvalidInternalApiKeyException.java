package com.fitness.userservice.exception;

public class InvalidInternalApiKeyException extends RuntimeException {

    public InvalidInternalApiKeyException() {
        super("Missing or invalid internal API key");
    }
}
