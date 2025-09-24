package com.teleshare.service;

import com.teleshare.config.TelegramProperties;
import com.teleshare.exception.TelegramUploadException;
import com.teleshare.exception.TelegramDownloadException;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service for direct file streaming to/from Telegram
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramStreamService {

    private final SimpleTelegramClient telegramClient;
    private final TelegramProperties telegramProperties;

    /**
     * Upload file directly from InputStream to Telegram without local storage
     */
    public String uploadFileDirectly(InputStream fileStream, String filename, long fileSize) throws IOException {
        log.info("Starting direct upload: {} ({} bytes)", filename, fileSize);

        if (fileSize > 10 * 1024 * 1024) { // > 10MB - use large file upload
            return uploadLargeFileStreaming(fileStream, filename, fileSize);
        } else { // <= 10MB - upload as single file
            return uploadSmallFileStreaming(fileStream, filename, fileSize);
        }
    }

    /**
     * Upload large files (>10MB)
     */
    private String uploadLargeFileStreaming(InputStream fileStream, String filename, long fileSize) throws IOException {
        log.info("Large file upload: {} ({} bytes)", filename, fileSize);

        try {
            // Create temporary file
            File tempFile = File.createTempFile("teleshare_", "_" + sanitizeFilename(filename));
            tempFile.deleteOnExit();

            // Write InputStream to temporary file
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                IOUtils.copy(fileStream, fos);
            }

            // Send to channel
            TdApi.SendMessage sendMessage = new TdApi.SendMessage();
            sendMessage.chatId = Long.parseLong(telegramProperties.getStorageChannelId());
            sendMessage.inputMessageContent = new TdApi.InputMessageDocument(
                new TdApi.InputFileLocal(tempFile.getAbsolutePath()),
                null, false, new TdApi.FormattedText(filename, null)
            );

            CompletableFuture<TdApi.Message> future = new CompletableFuture<>();
            telegramClient.send(sendMessage, result -> {
                if (result.isError()) {
                    future.completeExceptionally(new RuntimeException("Upload failed: " + result.getError().message));
                } else {
                    future.complete(result.get());
                }
            });

            TdApi.Message message = future.get(90, TimeUnit.SECONDS);

            // Clean up
            if (tempFile.exists()) {
                tempFile.delete();
            }

            log.info("Large file uploaded successfully: {} (Message ID: {})", filename, message.id);
            return String.valueOf(message.id);

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
            // Create temporary file
            File tempFile = File.createTempFile("teleshare_", "_" + sanitizeFilename(filename));
            tempFile.deleteOnExit();

            // Write InputStream to temporary file
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                IOUtils.copy(fileStream, fos);
            }

            // Send directly to storage channel
            TdApi.SendMessage sendMessage = new TdApi.SendMessage();
            sendMessage.chatId = Long.parseLong(telegramProperties.getStorageChannelId());
            sendMessage.inputMessageContent = new TdApi.InputMessageDocument(
                new TdApi.InputFileLocal(tempFile.getAbsolutePath()),
                null, false, new TdApi.FormattedText(filename, null)
            );

            CompletableFuture<TdApi.Message> future = new CompletableFuture<>();
            telegramClient.send(sendMessage, result -> {
                if (result.isError()) {
                    future.completeExceptionally(new RuntimeException("Upload failed: " + result.getError().message));
                } else {
                    future.complete(result.get());
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
            telegramClient.send(getMessage, result -> {
                if (result.isError()) {
                    future.completeExceptionally(new RuntimeException("Get message failed: " + result.getError().message));
                } else {
                    future.complete(result.get());
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
            telegramClient.send(downloadFile, result -> {
                if (result.isError()) {
                    downloadFuture.completeExceptionally(new RuntimeException("Download failed: " + result.getError().message));
                } else {
                    downloadFuture.complete(result.get());
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

            CompletableFuture<TdApi.Ok> future = new CompletableFuture<>();
            telegramClient.send(deleteMessage, result -> {
                if (result.isError()) {
                    future.completeExceptionally(new RuntimeException("Delete failed: " + result.getError().message));
                } else {
                    future.complete(result.get());
                }
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
            telegramClient.send(new TdApi.GetAuthorizationState(), result -> {
                if (result.isError()) {
                    future.completeExceptionally(new RuntimeException("Get authorization state failed: " + result.getError().message));
                } else {
                    future.complete(result.get() instanceof TdApi.AuthorizationStateReady);
                }
            });
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to check client status: {}", e.getMessage());
            return false;
        }
    }
}
