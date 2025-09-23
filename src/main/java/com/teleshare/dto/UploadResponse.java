package com.teleshare.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Response DTO for file upload operation
 */
@Data
@Builder
public class UploadResponse {
    
    /**
     * 6-digit access code for file download
     */
    private String accessCode;
    
    /**
     * Unique file identifier
     */
    private Long fileId;
    
    /**
     * Original filename
     */
    private String filename;
    
    /**
     * File size in bytes
     */
    private Long fileSize;
    
    /**
     * When the file will expire and be deleted
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expirationTime;
    
    /**
     * Maximum allowed file size
     */
    private String maxFileSize;
    
    /**
     * Success/status message
     */
    private String message;
    
    /**
     * File MIME type
     */
    private String mimeType;
    
    /**
     * Maximum number of downloads allowed
     */
    @Builder.Default
    private Integer maxDownloads = 50;
}