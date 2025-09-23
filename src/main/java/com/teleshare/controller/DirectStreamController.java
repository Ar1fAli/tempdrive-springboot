package com.teleshare.controller;

import com.teleshare.entity.FileMetadata;
import com.teleshare.exception.FileNotFoundException;
import com.teleshare.service.FileMetadataService;
import com.teleshare.service.TelegramStreamService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Controller for direct file streaming from Telegram
 */
@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
@Slf4j
public class DirectStreamController {
    
    private final TelegramStreamService telegramService;
    private final FileMetadataService metadataService;
    
    /**
     * Stream file directly from Telegram to client
     * 
     * @param messageId Telegram message ID containing the file
     * @param response HTTP response for streaming
     * @return Streaming response body
     */
    @GetMapping("/download/{messageId}")
    public ResponseEntity<StreamingResponseBody> streamFromTelegram(
            @PathVariable String messageId,
            HttpServletResponse response) {
        
        log.info("Stream request for message ID: {}", messageId);
        
        // Validate message exists and get metadata
        FileMetadata metadata = metadataService.findByTelegramMessageId(messageId)
            .orElseThrow(() -> {
                log.warn("File not found for message ID: {}", messageId);
                return new FileNotFoundException("File not found");
            });
            
        // Check expiration
        if (metadata.getExpirationAt().isBefore(LocalDateTime.now())) {
            log.warn("Attempt to download expired file: {} (expired: {})", 
                    metadata.getOriginalFilename(), metadata.getExpirationAt());
            throw new FileNotFoundException("File has expired");
        }
        
        // Check if upload was completed
        if (metadata.getStatus() != FileMetadata.FileStatus.UPLOAD_COMPLETED) {
            log.warn("Attempt to download incomplete file: {} (status: {})", 
                    metadata.getOriginalFilename(), metadata.getStatus());
            throw new FileNotFoundException("File upload not completed");
        }
        
        log.info("Starting stream for file: {} (size: {} bytes)", 
                metadata.getOriginalFilename(), metadata.getFileSize());
        
        // Create streaming response
        StreamingResponseBody stream = outputStream -> {
            try (InputStream telegramStream = telegramService.getFileStream(messageId)) {
                
                long bytesStreamed = IOUtils.copyLarge(telegramStream, outputStream);
                outputStream.flush();
                
                log.info("File streaming completed: {} ({} bytes streamed)", 
                        metadata.getOriginalFilename(), bytesStreamed);
                        
            } catch (IOException e) {
                log.error("Streaming failed for file {}: {}", 
                        metadata.getOriginalFilename(), e.getMessage(), e);
                throw new RuntimeException("File streaming failed: " + e.getMessage(), e);
            }
        };
        
        // Encode filename for safe HTTP header
        String encodedFilename = URLEncoder.encode(
            metadata.getOriginalFilename(), StandardCharsets.UTF_8
        ).replace("+", "%20");
        
        // Determine content type
        String contentType = metadata.getMimeType() != null ? 
            metadata.getMimeType() : "application/octet-stream";
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, contentType)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + encodedFilename)
            .header(HttpHeaders.CONTENT_LENGTH, 
                String.valueOf(metadata.getFileSize()))
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .header("Pragma", "no-cache")
            .header("Expires", "0")
            .header("X-File-Name", metadata.getOriginalFilename())
            .header("X-File-Size", String.valueOf(metadata.getFileSize()))
            .body(stream);
    }
}