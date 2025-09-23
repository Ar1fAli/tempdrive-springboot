package com.teleshare.repository;

import com.teleshare.entity.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for FileMetadata entity operations
 */
@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {
    
    /**
     * Find file by session ID
     */
    Optional<FileMetadata> findBySessionId(String sessionId);
    
    /**
     * Find file by Telegram message ID
     */
    Optional<FileMetadata> findByTelegramMessageId(String telegramMessageId);
    
    /**
     * Find file by ID and access code hash for validation
     */
    @Query("SELECT f FROM FileMetadata f WHERE f.id = ?1 AND f.accessCodeHash = ?2")
    Optional<FileMetadata> findByIdAndAccessCodeHash(Long id, String accessCodeHash);
    
    /**
     * Find expired files for cleanup
     */
    List<FileMetadata> findByExpirationAtBeforeAndStatusNot(
        LocalDateTime cutoffTime, 
        FileMetadata.FileStatus status
    );
    
    /**
     * Delete old records permanently
     */
    @Modifying
    @Query("DELETE FROM FileMetadata f WHERE f.status = ?1 AND f.expirationAt < ?2")
    int deleteByStatusAndExpirationAtBefore(
        FileMetadata.FileStatus status, 
        LocalDateTime cutoff
    );
    
    /**
     * Count uploads by IP for rate limiting
     */
    @Query("SELECT COUNT(f) FROM FileMetadata f WHERE f.clientIp = ?1 AND f.createdAt > ?2")
    int countByClientIpAndCreatedAtAfter(String clientIp, LocalDateTime since);
    
    /**
     * Get statistics for monitoring
     */
    @Query("SELECT COUNT(f) FROM FileMetadata f WHERE f.status = ?1")
    long countByStatus(FileMetadata.FileStatus status);
    
    /**
     * Get total storage used (sum of file sizes)
     */
    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM FileMetadata f WHERE f.status = 'UPLOAD_COMPLETED'")
    long getTotalStorageUsed();
}