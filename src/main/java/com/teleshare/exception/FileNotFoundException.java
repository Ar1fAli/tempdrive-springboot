package com.teleshare.exception;

/**
 * Exception thrown when requested file is not found
 */
public class FileNotFoundException extends RuntimeException {
    
    public FileNotFoundException(String message) {
        super(message);
    }
}