package com.teleshare.exception;

/**
 * Exception thrown when invalid access code is provided
 */
public class InvalidAccessCodeException extends RuntimeException {
    
    public InvalidAccessCodeException(String message) {
        super(message);
    }
}