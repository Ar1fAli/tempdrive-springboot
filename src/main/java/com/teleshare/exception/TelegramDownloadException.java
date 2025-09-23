package com.teleshare.exception;

/**
 * Exception thrown when Telegram file download fails
 */
public class TelegramDownloadException extends RuntimeException {
    
    public TelegramDownloadException(String message) {
        super(message);
    }
    
    public TelegramDownloadException(String message, Throwable cause) {
        super(message, cause);
    }
}