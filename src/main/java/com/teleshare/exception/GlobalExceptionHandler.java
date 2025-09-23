package com.teleshare.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for REST API errors
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleFileNotFound(FileNotFoundException e) {
        log.warn("File not found: {}", e.getMessage());
        return createErrorResponse(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", e.getMessage());
    }
    
    @ExceptionHandler(InvalidAccessCodeException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidAccessCode(InvalidAccessCodeException e) {
        log.warn("Invalid access code: {}", e.getMessage());
        return createErrorResponse(HttpStatus.UNAUTHORIZED, "INVALID_ACCESS_CODE", e.getMessage());
    }
    
    @ExceptionHandler(FileTooLargeException.class)
    public ResponseEntity<Map<String, Object>> handleFileTooLarge(FileTooLargeException e) {
        log.warn("File too large: {}", e.getMessage());
        return createErrorResponse(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", e.getMessage());
    }
    
    @ExceptionHandler(FileExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleFileExpired(FileExpiredException e) {
        log.warn("File expired: {}", e.getMessage());
        return createErrorResponse(HttpStatus.GONE, "FILE_EXPIRED", e.getMessage());
    }
    
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitExceeded(RateLimitExceededException e) {
        log.warn("Rate limit exceeded: {}", e.getMessage());
        return createErrorResponse(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", e.getMessage());
    }
    
    @ExceptionHandler(DownloadLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleDownloadLimitExceeded(DownloadLimitExceededException e) {
        log.warn("Download limit exceeded: {}", e.getMessage());
        return createErrorResponse(HttpStatus.FORBIDDEN, "DOWNLOAD_LIMIT_EXCEEDED", e.getMessage());
    }
    
    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidFile(InvalidFileException e) {
        log.warn("Invalid file: {}", e.getMessage());
        return createErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_FILE", e.getMessage());
    }
    
    @ExceptionHandler(TelegramUploadException.class)
    public ResponseEntity<Map<String, Object>> handleTelegramUpload(TelegramUploadException e) {
        log.error("Telegram upload error: {}", e.getMessage(), e);
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED", 
            "File upload failed. Please try again later.");
    }
    
    @ExceptionHandler(TelegramDownloadException.class)
    public ResponseEntity<Map<String, Object>> handleTelegramDownload(TelegramDownloadException e) {
        log.error("Telegram download error: {}", e.getMessage(), e);
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "DOWNLOAD_FAILED", 
            "File download failed. Please try again later.");
    }
    
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("Upload size exceeded: {}", e.getMessage());
        return createErrorResponse(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", 
            "File size exceeds 1GB limit");
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Invalid argument: {}", e.getMessage());
        return createErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", e.getMessage());
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", 
            "An unexpected error occurred. Please try again later.");
    }
    
    /**
     * Create standardized error response
     */
    private ResponseEntity<Map<String, Object>> createErrorResponse(HttpStatus status, String code, String message) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", true);
        errorResponse.put("code", code);
        errorResponse.put("message", message);
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("status", status.value());
        
        return ResponseEntity.status(status).body(errorResponse);
    }
}