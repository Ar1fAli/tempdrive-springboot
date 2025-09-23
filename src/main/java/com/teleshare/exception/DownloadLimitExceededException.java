package com.teleshare.exception;

/**
 * Exception thrown when file download limit is exceeded
 */
public class DownloadLimitExceededException extends RuntimeException {
    
    public DownloadLimitExceededException(String message) {
        super(message);
    }
}