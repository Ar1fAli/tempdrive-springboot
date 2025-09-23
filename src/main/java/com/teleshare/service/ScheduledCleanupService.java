package com.teleshare.service;

import com.teleshare.entity.FileMetadata;
import com.teleshare.repository.FileMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled service for automatic cleanup of expired files and maintenance tasks
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledCleanupService {
    
    private final FileMetadataRepository fileRepository;
    private final TelegramStreamService telegramService;
    private final RateLimitingService rateLimitingService;
    
    /**
     * Daily cleanup of expired files (7-day retention)
     * Runs every day at 2:00 AM
     */
    @Scheduled(cron = "0 0 2 * * ?", zone = "UTC")
    @Transactional
    public void cleanupExpiredFiles() {
        log.info("Starting daily cleanup of expired files");
        
        LocalDateTime cutoffTime = LocalDateTime.now();
        List<FileMetadata> expiredFiles = fileRepository
            .findByExpirationAtBeforeAndStatusNot(cutoffTime, FileMetadata.FileStatus.DELETED);
        
        if (expiredFiles.isEmpty()) {
            log.info("No expired files found for cleanup");
            return;
        }
        
        log.info("Found {} expired files to clean up", expiredFiles.size());
        
        int successCount = 0;
        int errorCount = 0;
        long totalSizeDeleted = 0;
        
        for (FileMetadata file : expiredFiles) {
            try {
                // Delete from Telegram if message ID exists
                if (file.getTelegramMessageId() != null && 
                    !file.getTelegramMessageId().isEmpty()) {
                    telegramService.deleteMessage(file.getTelegramMessageId());
                }
                
                // Update status to deleted
                file.setStatus(FileMetadata.FileStatus.DELETED);
                fileRepository.save(file);
                
                totalSizeDeleted += file.getFileSize() != null ? file.getFileSize() : 0;
                successCount++;
                
                if (successCount % 10 == 0) {
                    log.debug("Cleanup progress: {}/{} files processed", 
                            successCount + errorCount, expiredFiles.size());
                }
                
            } catch (Exception e) {
                errorCount++;
                log.error("Failed to delete expired file '{}' (ID: {}): {}", 
                    file.getOriginalFilename(), file.getId(), e.getMessage());
            }
        }
        
        log.info("Daily cleanup completed - Success: {}, Errors: {}, Total size freed: {} MB", 
                successCount, errorCount, totalSizeDeleted / (1024 * 1024));
    }
    
    /**
     * Weekly cleanup of old database records
     * Runs every Sunday at 3:00 AM
     */
    @Scheduled(cron = "0 0 3 * * SUN", zone = "UTC")
    @Transactional
    public void cleanupOldRecords() {
        log.info("Starting weekly cleanup of old database records");
        
        // Delete records that have been marked as deleted for more than 30 days
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        int deleted = fileRepository.deleteByStatusAndExpirationAtBefore(
            FileMetadata.FileStatus.DELETED, cutoff);
            
        log.info("Weekly cleanup completed - Removed {} old deleted records from database", deleted);
    }
    
    /**
     * Cleanup rate limiting cache
     * Runs every hour
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void cleanupRateLimitingCache() {
        try {
            rateLimitingService.cleanupOldEntries();
            log.debug("Rate limiting cache cleanup completed");
        } catch (Exception e) {
            log.warn("Rate limiting cache cleanup failed: {}", e.getMessage());
        }
    }
    
    /**
     * System health check and statistics logging
     * Runs every 15 minutes
     */
    @Scheduled(fixedRate = 900000) // Every 15 minutes
    public void systemHealthCheck() {
        try {
            long totalFiles = fileRepository.count();
            long activeFiles = fileRepository.countByStatus(FileMetadata.FileStatus.UPLOAD_COMPLETED);
            long pendingUploads = fileRepository.countByStatus(FileMetadata.FileStatus.UPLOAD_PENDING);
            long failedUploads = fileRepository.countByStatus(FileMetadata.FileStatus.UPLOAD_FAILED);
            
            // Log stats every hour (4 * 15min intervals)
            if (System.currentTimeMillis() % (4 * 900000) < 900000) {
                log.info("System Health - Total: {}, Active: {}, Pending: {}, Failed: {}", 
                        totalFiles, activeFiles, pendingUploads, failedUploads);
            }
            
            // Alert if too many pending or failed uploads
            if (pendingUploads > 50) {
                log.warn("High number of pending uploads: {}", pendingUploads);
            }
            
            if (failedUploads > 100) {
                log.warn("High number of failed uploads: {}", failedUploads);
            }
            
        } catch (Exception e) {
            log.error("System health check failed: {}", e.getMessage());
        }
    }
    
    /**
     * Mark files as expired that have passed their expiration time
     * but haven't been cleaned up yet. Runs every 6 hours.
     */
    @Scheduled(fixedRate = 21600000) // Every 6 hours
    @Transactional
    public void markExpiredFiles() {
        try {
            LocalDateTime now = LocalDateTime.now();
            
            List<FileMetadata> expiredFiles = fileRepository.findByExpirationAtBeforeAndStatusNot(
                now, FileMetadata.FileStatus.EXPIRED);
                
            expiredFiles = expiredFiles.stream()
                .filter(f -> f.getStatus() == FileMetadata.FileStatus.UPLOAD_COMPLETED)
                .toList();
            
            if (!expiredFiles.isEmpty()) {
                for (FileMetadata file : expiredFiles) {
                    file.setStatus(FileMetadata.FileStatus.EXPIRED);
                }
                fileRepository.saveAll(expiredFiles);
                
                log.info("Marked {} files as expired", expiredFiles.size());
            }
            
        } catch (Exception e) {
            log.error("Failed to mark expired files: {}", e.getMessage());
        }
    }
}