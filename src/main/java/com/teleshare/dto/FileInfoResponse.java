package com.teleshare.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Response DTO for file information retrieval
 */
@Data
@Builder
public class FileInfoResponse {
    
    private Long fileId;
    private String filename;
    private Long fileSize;
    private String mimeType;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime uploadedAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastAccessedAt;
    
    private Integer downloadCount;
    private Integer maxDownloads;
    private String status;
    
    /**
     * Formatted file size (e.g., "15.2 MB")
     */
    private String formattedSize;
    
    /**
     * Days remaining until expiration
     */
    private Long daysUntilExpiration;
}