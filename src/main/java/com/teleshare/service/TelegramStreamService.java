package com.teleshare.service;

import com.teleshare.config.TelegramProperties;
import com.teleshare.exception.TelegramUploadException;
import com.teleshare.exception.TelegramDownloadException;
import it.tdlight.client.Client;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service for direct file streaming to/from Telegram
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramStreamService {
    
    private final Client telegramClient;
    private final TelegramProperties telegramProperties;
    
    /**
     * Upload file directly from InputStream to Telegram without local storage
     */
    public String uploadFileDirectly(InputStream fileStream, String filename, long fileSize) throws IOException {
        
        log.info("Starting direct upload: {} ({} bytes)", filename, fileSize);
        
        if (fileSize > 10 * 1024 * 1024) { // > 10MB - use chunked upload
            return uploadLargeFileStreaming(fileStream, filename, fileSize);
        } else { // <= 10MB - upload as single file
            return uploadSmallFileStreaming(fileStream, filename, fileSize);
        }
    }
    
    /**
     * Upload large files (>10MB) using chunked streaming
     */
    private String uploadLargeFileStreaming(InputStream fileStream, String filename, long fileSize) throws IOException {
        
        long fileId = System.currentTimeMillis(); // Unique file ID
        int partSize = 512 * 1024; // 512KB chunks
        int totalParts = (int) Math.ceil((double) fileSize / partSize);
        
        log.info("Large file upload - File: {}, Size: {} bytes, Parts: {}", filename, fileSize, totalParts);
        
        try (BufferedInputStream bufferedStream = new BufferedInputStream(fileStream, partSize)) {
            
            byte[] buffer = new byte[partSize];
            int filePart = 0;
            int bytesRead;
            long totalBytesRead = 0;
            
            // Stream file in chunks directly to Telegram
            while ((bytesRead = bufferedStream.read(buffer)) != -1) {
                
                // Create chunk for this part
                byte[] chunk = Arrays.copyOf(buffer, bytesRead);
                totalBytesRead += bytesRead;
                
                // Upload part directly to Telegram
                TdApi.UploadSaveBigFilePart uploadPart = 
                    new TdApi.UploadSaveBigFilePart(fileId, filePart, totalParts, chunk);
                
                // Send to Telegram synchronously
                CompletableFuture<TdApi.Object> future = new CompletableFuture<>();
                telegramClient.send(uploadPart, object -> {
                    if (object instanceof TdApi.Ok) {
                        future.complete(object);
                    } else {
                        future.completeExceptionally(
                            new RuntimeException("Upload part failed: " + object));
                    }
                });
                
                // Wait for this chunk to complete before next
                future.get(30, TimeUnit.SECONDS);
                filePart++;
                
                // Log progress every 10 parts
                if (filePart % 10 == 0) {
                    double progress = (double) totalBytesRead / fileSize * 100;
                    log.debug("Upload progress: {}/{} parts ({:.1f}%)", filePart, totalParts, progress);
                }
            }
            
            log.info("All parts uploaded successfully. Sending to channel...");
            
            // Send completed file as document to storage channel
            return sendCompletedFileToChannel(fileId, totalParts, filename);
            
        } catch (Exception e) {
            log.error("Large file upload failed for {}: {}", filename, e.getMessage(), e);
            throw new TelegramUploadException("Direct streaming failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Upload small files (<=10MB) as single upload
     */
    private String uploadSmallFileStreaming(InputStream fileStream, String filename, long fileSize) throws IOException {
        
        log.info("Small file upload: {} ({} bytes)", filename, fileSize);
        
        try {
            // Read entire small file into memory
            byte[] fileBytes = IOUtils.toByteArray(fileStream);
            
            // Create temporary file for Telegram upload
            File tempFile = File.createTempFile("teleshare_", "_" + sanitizeFilename(filename));
            tempFile.deleteOnExit();
            
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(fileBytes);
            }
            
            // Send directly to storage channel
            TdApi.SendMessage sendMessage = new TdApi.SendMessage();
            sendMessage.chatId = Long.parseLong(telegramProperties.getStorageChannelId());
            sendMessage.inputMessageContent = new TdApi.InputMessageDocument(
                new TdApi.InputFileLocal(tempFile.getAbsolutePath()),
                null, false, null
            );
            
            CompletableFuture<TdApi.Message> future = new CompletableFuture<>();
            telegramClient.send(sendMessage, object -> {
                if (object instanceof TdApi.Message) {
                    future.complete((TdApi.Message) object);
                } else {
                    future.completeExceptionally(
                        new RuntimeException("Upload failed: " + object));
                }
            });
            
            TdApi.Message message = future.get(60, TimeUnit.SECONDS);
            
            // Clean up temp file
            if (tempFile.exists()) {
                tempFile.delete();
            }
            
            log.info("Small file uploaded successfully: {} (Message ID: {})", filename, message.id);
            return String.valueOf(message.id);
            
        } catch (Exception e) {
            log.error("Small file upload failed for {}: {}", filename, e.getMessage(), e);
            throw new TelegramUploadException("Small file upload failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Send large file parts as completed document to channel
     */
    private String sendCompletedFileToChannel(long fileId, int totalParts, String filename) throws Exception {
        
        TdApi.SendMessage sendMessage = new TdApi.SendMessage();
        sendMessage.chatId = Long.parseLong(telegramProperties.getStorageChannelId());
        sendMessage.inputMessageContent = new TdApi.InputMessageDocument(
            new TdApi.InputFileBig(fileId, totalParts, filename),
            null, false, null
        );
        
        CompletableFuture<TdApi.Message> future = new CompletableFuture<>();
        telegramClient.send(sendMessage, object -> {
            if (object instanceof TdApi.Message) {
                future.complete((TdApi.Message) object);
            } else {
                future.completeExceptionally(new RuntimeException("Send to channel failed: " + object));
            }
        });
        
        TdApi.Message message = future.get(90, TimeUnit.SECONDS);
        log.info("Large file sent to channel successfully: {} (Message ID: {})", filename, message.id);
        return String.valueOf(message.id);
    }
    
    /**
     * Get file stream from Telegram for direct download
     */
    public InputStream getFileStream(String messageId) {
        try {
            log.info("Getting file stream for message ID: {}", messageId);
            
            // Get file info from message
            TdApi.GetMessage getMessage = new TdApi.GetMessage(
                Long.parseLong(telegramProperties.getStorageChannelId()), 
                Long.parseLong(messageId)
            );
            
            CompletableFuture<TdApi.Message> future = new CompletableFuture<>();
            telegramClient.send(getMessage, object -> {
                if (object instanceof TdApi.Message) {
                    future.complete((TdApi.Message) object);
                } else {
                    future.completeExceptionally(new RuntimeException("Get message failed: " + object));
                }
            });
            
            TdApi.Message message = future.get(30, TimeUnit.SECONDS);
            
            if (!(message.content instanceof TdApi.MessageDocument)) {
                throw new TelegramDownloadException("Message does not contain a document");
            }
            
            TdApi.Document document = ((TdApi.MessageDocument) message.content).document;
            
            // Download file
            TdApi.DownloadFile downloadFile = new TdApi.DownloadFile(
                document.document.id, 1, 0, 0, false
            );
            
            CompletableFuture<TdApi.File> downloadFuture = new CompletableFuture<>();
            telegramClient.send(downloadFile, object -> {
                if (object instanceof TdApi.File) {
                    downloadFuture.complete((TdApi.File) object);
                } else {
                    downloadFuture.completeExceptionally(new RuntimeException("Download failed: " + object));
                }
            });
            
            TdApi.File file = downloadFuture.get(90, TimeUnit.SECONDS);
            
            if (!file.local.isDownloadingCompleted) {
                throw new TelegramDownloadException("File download not completed");
            }
            
            log.info("File stream ready for message ID: {}", messageId);
            return new FileInputStream(file.local.path);
            
        } catch (Exception e) {
            log.error("Failed to get file stream for message {}: {}", messageId, e.getMessage(), e);
            throw new TelegramDownloadException("Failed to get file stream: " + e.getMessage(), e);
        }
    }
    
    /**
     * Delete message from Telegram channel
     */
    public void deleteMessage(String messageId) {
        try {
            log.info("Deleting message: {}", messageId);
            
            TdApi.DeleteMessages deleteMessage = new TdApi.DeleteMessages(
                Long.parseLong(telegramProperties.getStorageChannelId()),
                new long[]{Long.parseLong(messageId)},
                true // Revoke for all users
            );
            
            CompletableFuture<TdApi.Object> future = new CompletableFuture<>();
            telegramClient.send(deleteMessage, object -> {
                future.complete(object);
            });
            
            future.get(30, TimeUnit.SECONDS);
            log.info("Message deleted successfully: {}", messageId);
            
        } catch (Exception e) {
            log.error("Failed to delete message {}: {}", messageId, e.getMessage());
            // Don't throw exception - cleanup should continue for other files
        }
    }
    
    /**
     * Sanitize filename for safe file operations
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) return "unnamed_file";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
    
    /**
     * Check if Telegram client is ready
     */
    public boolean isClientReady() {
        try {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            telegramClient.send(new TdApi.GetAuthorizationState(), object -> {
                future.complete(object instanceof TdApi.AuthorizationStateReady);
            });
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to check client status: {}", e.getMessage());
            return false;
        }
    }
}