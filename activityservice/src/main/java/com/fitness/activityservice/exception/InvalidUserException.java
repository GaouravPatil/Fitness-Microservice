package com.fitness.activityservice.exception;

public class InvalidUserException extends RuntimeException {

    public InvalidUserException(String userId) {
        super("User with id '%s' does not exist".formatted(userId));
    }
}
