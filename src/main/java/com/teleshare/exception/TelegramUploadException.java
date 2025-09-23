package com.teleshare.exception;

/**
 * Exception thrown when Telegram file upload fails
 */
public class TelegramUploadException extends RuntimeException {
    
    public TelegramUploadException(String message) {
        super(message);
    }
    
    public TelegramUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}