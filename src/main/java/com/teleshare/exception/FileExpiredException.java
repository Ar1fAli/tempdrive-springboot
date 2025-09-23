package com.teleshare.exception;

/**
 * Exception thrown when trying to access an expired file
 */
public class FileExpiredException extends RuntimeException {
    
    public FileExpiredException(String message) {
        super(message);
    }
}