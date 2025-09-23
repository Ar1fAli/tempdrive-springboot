package com.teleshare.exception;

/**
 * Exception thrown when file validation fails
 */
public class InvalidFileException extends RuntimeException {
    
    public InvalidFileException(String message) {
        super(message);
    }
}