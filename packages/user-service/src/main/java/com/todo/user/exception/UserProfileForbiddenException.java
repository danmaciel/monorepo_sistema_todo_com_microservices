package com.todo.user.exception;

/** Raised when an authenticated user tries to read a different user's profile. */
public class UserProfileForbiddenException extends RuntimeException {
    public UserProfileForbiddenException(String message) {
        super(message);
    }
}
