package com.teleshare.controller;

import com.teleshare.dto.DownloadResponse;
import com.teleshare.dto.FileInfoResponse;
import com.teleshare.dto.UploadResponse;
import com.teleshare.entity.FileMetadata;
import com.teleshare.exception.*;
import com.teleshare.service.FileMetadataService;
import com.teleshare.service.RateLimitingService;
import com.teleshare.service.TelegramStreamService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * REST API Controller for file upload/download operations
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class FileOperationsController {
    
    private final TelegramStreamService telegramService;
    private final FileMetadataService metadataService;
    private final RateLimitingService rateLimitingService;
    
    @Value("${app.allowed-file-types:}")
    private String allowedFileTypes;
    
    /**
     * Upload file endpoint - streams directly to Telegram
     * 
     * @param file The file to upload (max 1GB)
     * @param request HTTP request for IP extraction
     * @return Upload response with access code and file info
     */
    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) throws IOException {
        
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        
        log.info("Upload request - File: {}, Size: {} bytes, IP: {}", 
                file.getOriginalFilename(), file.getSize(), clientIp);
        
        // Rate limiting check
        if (!rateLimitingService.isUploadAllowed(clientIp)) {
            throw new RateLimitExceededException(
                "Upload limit exceeded. You can upload maximum " + 
                rateLimitingService.getRemainingUploads(clientIp) + " more files this hour.");
        }
        
        // File validation
        validateFile(file);
        
        // Generate access code
        String accessCode = generateSixDigitCode();
        
        // Create metadata entry before upload
        FileMetadata metadata = FileMetadata.builder()
            .sessionId(UUID.randomUUID().toString())
            .originalFilename(file.getOriginalFilename())
            .accessCodeHash(metadataService.hashAccessCode(accessCode))
            .fileSize(file.getSize())
            .mimeType(file.getContentType())
            .clientIp(clientIp)
            .userAgent(userAgent)
            .createdAt(LocalDateTime.now())
            .expirationAt(LocalDateTime.now().plusDays(7))
            .status(FileMetadata.FileStatus.UPLOAD_PENDING)
            .build();
            
        metadataService.save(metadata);
        
        try {
            // Stream directly to Telegram WITHOUT saving locally
            String telegramMessageId = telegramService.uploadFileDirectly(
                file.getInputStream(),
                file.getOriginalFilename(),
                file.getSize()
            );
            
            // Update metadata with successful upload
            metadata.setTelegramMessageId(telegramMessageId);
            metadata.setStatus(FileMetadata.FileStatus.UPLOAD_COMPLETED);
            metadata.setUploadCompletedAt(LocalDateTime.now());
            metadataService.save(metadata);
            
            // Record upload for rate limiting
            rateLimitingService.recordUploadAttempt(clientIp);
            
            log.info("Upload completed successfully - File: {}, ID: {}, Message: {}", 
                    file.getOriginalFilename(), metadata.getId(), telegramMessageId);
            
            return ResponseEntity.ok(UploadResponse.builder()
                .accessCode(accessCode)
                .fileId(metadata.getId())
                .filename(file.getOriginalFilename())
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .expirationTime(metadata.getExpirationAt())
                .maxFileSize("1GB")
                .message("File uploaded successfully to Telegram")
                .build());
                
        } catch (Exception e) {
            // Update metadata with failed status
            metadata.setStatus(FileMetadata.FileStatus.UPLOAD_FAILED);
            metadataService.save(metadata);
            
            log.error("Upload failed - File: {}, Error: {}", file.getOriginalFilename(), e.getMessage(), e);
            throw new TelegramUploadException("Upload failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate download URL endpoint
     * 
     * @param fileId File identifier
     * @param code 6-digit access code
     * @return Download response with direct Telegram URL
     */
    @GetMapping("/download-url")
    public ResponseEntity<DownloadResponse> generateDownloadUrl(
            @RequestParam String fileId,
            @RequestParam String code) {
        
        log.info("Download URL request - FileID: {}", fileId);
        
        FileMetadata metadata = metadataService.validateAccess(fileId, code);
        
        // Check expiration
        if (metadata.getExpirationAt().isBefore(LocalDateTime.now())) {
            throw new FileExpiredException("File has expired on " + metadata.getExpirationAt());
        }
        
        // Check upload completion
        if (metadata.getStatus() != FileMetadata.FileStatus.UPLOAD_COMPLETED) {
            throw new FileNotFoundException("File upload not completed or failed");
        }
        
        // Check download limit
        if (metadata.getDownloadCount() >= 50) {
            throw new DownloadLimitExceededException(
                "Download limit reached (50/50). File cannot be downloaded anymore.");
        }
        
        // Generate direct download URL
        String directDownloadUrl = "/api/stream/download/" + metadata.getTelegramMessageId();
        
        // Update access metrics
        metadata.setDownloadCount(metadata.getDownloadCount() + 1);
        metadata.setLastAccessAt(LocalDateTime.now());
        metadataService.save(metadata);
        
        log.info("Download URL generated - File: {}, Downloads: {}/50", 
                metadata.getOriginalFilename(), metadata.getDownloadCount());
        
        return ResponseEntity.ok(DownloadResponse.builder()
            .downloadUrl(directDownloadUrl)
            .filename(metadata.getOriginalFilename())
            .fileSize(metadata.getFileSize())
            .mimeType(metadata.getMimeType())
            .validUntil(LocalDateTime.now().plusHours(2))
            .downloadsRemaining(50 - metadata.getDownloadCount())
            .totalDownloads(metadata.getDownloadCount())
            .build());
    }
    
    /**
     * Get file information endpoint
     * 
     * @param fileId File identifier
     * @param code 6-digit access code
     * @return File information response
     */
    @GetMapping("/file-info")
    public ResponseEntity<FileInfoResponse> getFileInfo(
            @RequestParam String fileId,
            @RequestParam String code) {
        
        FileMetadata metadata = metadataService.validateAccess(fileId, code);
        
        return ResponseEntity.ok(FileInfoResponse.builder()
            .fileId(metadata.getId())
            .filename(metadata.getOriginalFilename())
            .fileSize(metadata.getFileSize())
            .formattedSize(metadataService.formatFileSize(metadata.getFileSize()))
            .mimeType(metadata.getMimeType())
            .uploadedAt(metadata.getCreatedAt())
            .expiresAt(metadata.getExpirationAt())
            .lastAccessedAt(metadata.getLastAccessAt())
            .downloadCount(metadata.getDownloadCount())
            .maxDownloads(50)
            .status(metadata.getStatus().toString())
            .daysUntilExpiration(metadataService.getDaysUntilExpiration(metadata.getExpirationAt()))
            .build());
    }
    
    /**
     * Get system statistics (for monitoring)
     */
    @GetMapping("/stats")
    public ResponseEntity<FileMetadataService.SystemStats> getSystemStats() {
        return ResponseEntity.ok(metadataService.getSystemStats());
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        boolean telegramReady = telegramService.isClientReady();
        
        if (telegramReady) {
            return ResponseEntity.ok("Service is healthy. Telegram client is ready.");
        } else {
            return ResponseEntity.status(503).body("Service degraded. Telegram client not ready.");
        }
    }
    
    // Private helper methods
    
    private void validateFile(MultipartFile file) {
        // Check if file is empty
        if (file.isEmpty()) {
            throw new InvalidFileException("File is empty");
        }
        
        // Check file size (1GB limit)
        if (file.getSize() > 1024L * 1024 * 1024) {
            throw new FileTooLargeException("File exceeds 1GB limit");
        }
        
        // Check filename
        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new InvalidFileException("File must have a name");
        }
        
        // Check file type if restrictions are configured
        if (!allowedFileTypes.isEmpty()) {
            String mimeType = file.getContentType();
            Set<String> allowedTypes = Set.of(allowedFileTypes.split(","));
            
            if (mimeType == null || !allowedTypes.contains(mimeType.toLowerCase())) {
                throw new InvalidFileException("File type not allowed: " + mimeType);
            }
        }
    }
    
    private String generateSixDigitCode() {
        return String.format("%06d", new SecureRandom().nextInt(999999));
    }
    
    private String getClientIp(HttpServletRequest request) {
        // Check various headers for real IP (useful behind proxies/load balancers)
        String[] headers = {
            "X-Forwarded-For",
            "X-Real-IP",
            "CF-Connecting-IP", // Cloudflare
            "X-Client-IP"
        };
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Take first IP if comma-separated list
                return ip.split(",")[0].trim();
            }
        }
        
        return request.getRemoteAddr();
    }
}