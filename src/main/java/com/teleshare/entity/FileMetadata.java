package com.teleshare.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Entity representing file metadata stored in database.
 * Actual file content is stored in Telegram, only metadata is kept locally.
 */
@Entity
@Table(name = "file_metadata", indexes = {
    @Index(columnList = "sessionId"),
    @Index(columnList = "accessCodeHash"),
    @Index(columnList = "telegramMessageId"),
    @Index(columnList = "expirationAt"),
    @Index(columnList = "clientIp"),
    @Index(columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadata {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String sessionId;
    
    @Column(nullable = false)
    private String accessCodeHash;
    
    private String originalFilename;
    private String telegramMessageId;
    private Long fileSize;
    private String mimeType;
    
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private FileStatus status = FileStatus.UPLOAD_PENDING;
    
    private LocalDateTime createdAt;
    private LocalDateTime uploadCompletedAt;
    private LocalDateTime expirationAt;
    private LocalDateTime lastAccessAt;
    
    @Builder.Default
    private Integer downloadCount = 0;
    
    private String clientIp;
    private String userAgent;
    
    /**
     * File status enumeration
     */
    public enum FileStatus {
        UPLOAD_PENDING,
        UPLOAD_COMPLETED,
        UPLOAD_FAILED,
        EXPIRED,
        DELETED
    }
}