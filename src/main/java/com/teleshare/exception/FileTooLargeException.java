package com.teleshare.exception;

/**
 * Exception thrown when uploaded file exceeds size limit
 */
public class FileTooLargeException extends RuntimeException {
    
    public FileTooLargeException(String message) {
        super(message);
    }
}