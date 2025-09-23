package com.teleshare.service;

import com.teleshare.entity.FileMetadata;
import com.teleshare.repository.FileMetadataRepository;
import com.teleshare.exception.FileNotFoundException;
import com.teleshare.exception.InvalidAccessCodeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Service for file metadata operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileMetadataService {
    
    private final FileMetadataRepository fileRepository;
    
    /**
     * Save file metadata
     */
    public FileMetadata save(FileMetadata metadata) {
        return fileRepository.save(metadata);
    }
    
    /**
     * Find file by session ID
     */
    public Optional<FileMetadata> findBySessionId(String sessionId) {
        return fileRepository.findBySessionId(sessionId);
    }
    
    /**
     * Find file by Telegram message ID
     */
    public Optional<FileMetadata> findByTelegramMessageId(String messageId) {
        return fileRepository.findByTelegramMessageId(messageId);
    }
    
    /**
     * Validate file access with ID and access code
     */
    public FileMetadata validateAccess(String fileId, String accessCode) {
        Long id;
        try {
            id = Long.parseLong(fileId);
        } catch (NumberFormatException e) {
            log.warn("Invalid file ID format: {}", fileId);
            throw new FileNotFoundException("Invalid file ID format");
        }
        
        Optional<FileMetadata> fileOpt = fileRepository.findById(id);
        if (fileOpt.isEmpty()) {
            log.warn("File not found: {}", id);
            throw new FileNotFoundException("File not found");
        }
        
        FileMetadata file = fileOpt.get();
        
        // Validate access code
        if (!BCrypt.checkpw(accessCode, file.getAccessCodeHash())) {
            log.warn("Invalid access code for file: {}", id);
            throw new InvalidAccessCodeException("Invalid access code");
        }
        
        return file;
    }
    
    /**
     * Hash access code using BCrypt
     */
    public String hashAccessCode(String code) {
        return BCrypt.hashpw(code, BCrypt.gensalt(12));
    }
    
    /**
     * Get system statistics
     */
    public SystemStats getSystemStats() {
        return SystemStats.builder()
            .totalFiles(fileRepository.count())
            .activeFiles(fileRepository.countByStatus(FileMetadata.FileStatus.UPLOAD_COMPLETED))
            .expiredFiles(fileRepository.countByStatus(FileMetadata.FileStatus.EXPIRED))
            .failedUploads(fileRepository.countByStatus(FileMetadata.FileStatus.UPLOAD_FAILED))
            .totalStorageBytes(fileRepository.getTotalStorageUsed())
            .build();
    }
    
    /**
     * Format file size for display
     */
    public String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    /**
     * Calculate days until expiration
     */
    public long getDaysUntilExpiration(LocalDateTime expirationTime) {
        return ChronoUnit.DAYS.between(LocalDateTime.now(), expirationTime);
    }
    
    /**
     * System statistics data class
     */
    @lombok.Data
    @lombok.Builder
    public static class SystemStats {
        private long totalFiles;
        private long activeFiles;
        private long expiredFiles;
        private long failedUploads;
        private long totalStorageBytes;
    }
}