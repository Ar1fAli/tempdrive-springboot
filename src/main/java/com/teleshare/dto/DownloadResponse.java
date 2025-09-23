package com.teleshare.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Response DTO for download URL generation
 */
@Data
@Builder
public class DownloadResponse {
    
    /**
     * Direct download URL (streams from Telegram)
     */
    private String downloadUrl;
    
    /**
     * Original filename
     */
    private String filename;
    
    /**
     * File size in bytes
     */
    private Long fileSize;
    
    /**
     * When this download URL expires
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validUntil;
    
    /**
     * Number of downloads remaining
     */
    private Integer downloadsRemaining;
    
    /**
     * File MIME type
     */
    private String mimeType;
    
    /**
     * Total download count for this file
     */
    private Integer totalDownloads;
}