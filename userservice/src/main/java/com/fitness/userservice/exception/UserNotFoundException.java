package com.fitness.userservice.exception;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String userId) {
        super("User with id '%s' was not found".formatted(userId));
    }
}
