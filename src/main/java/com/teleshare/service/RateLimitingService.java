package com.teleshare.service;

import com.teleshare.repository.FileMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service for rate limiting upload operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitingService {
    
    private final FileMetadataRepository fileRepository;
    
    @Value("${app.max-uploads-per-hour-per-ip:5}")
    private int maxUploadsPerHour;
    
    // In-memory cache for rate limiting (consider Redis for production)
    private final ConcurrentMap<String, LocalDateTime> lastUploadTimes = new ConcurrentHashMap<>();
    
    /**
     * Check if upload is allowed for given IP address
     */
    public boolean isUploadAllowed(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            log.warn("No client IP provided for rate limiting");
            return true; // Allow if no IP (shouldn't happen in normal cases)
        }
        
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        int recentUploads = fileRepository.countByClientIpAndCreatedAtAfter(clientIp, oneHourAgo);
        
        boolean allowed = recentUploads < maxUploadsPerHour;
        
        if (!allowed) {
            log.warn("Rate limit exceeded for IP: {} ({} uploads in last hour)", clientIp, recentUploads);
        }
        
        return allowed;
    }
    
    /**
     * Record upload attempt for IP
     */
    public void recordUploadAttempt(String clientIp) {
        if (clientIp != null && !clientIp.isEmpty()) {
            lastUploadTimes.put(clientIp, LocalDateTime.now());
        }
    }
    
    /**
     * Get remaining uploads for IP
     */
    public int getRemainingUploads(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return maxUploadsPerHour;
        }
        
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        int recentUploads = fileRepository.countByClientIpAndCreatedAtAfter(clientIp, oneHourAgo);
        
        return Math.max(0, maxUploadsPerHour - recentUploads);
    }
    
    /**
     * Clean up old entries from in-memory cache
     */
    public void cleanupOldEntries() {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        lastUploadTimes.entrySet().removeIf(entry -> entry.getValue().isBefore(oneHourAgo));
    }
}