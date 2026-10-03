package com.municipal.tracker.exception;

public class TooManyLoginAttemptsException extends RuntimeException {
    public TooManyLoginAttemptsException() {
        super("Too many login attempts. Try again later");
    }
}
