package com.teleshare.config;

import it.tdlight.client.Client;
import it.tdlight.client.ClientManager;
import it.tdlight.client.TDLibSettings;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Telegram client configuration
 */
@Configuration
@Slf4j
public class TelegramConfig {
    
    @Autowired
    private TelegramProperties telegramProperties;
    
    /**
     * Creates and configures Telegram client for file operations
     */
    @Bean
    public Client telegramClient() {
        try {
            // Configure TDLib settings
            TDLibSettings settings = TDLibSettings.create();
            settings.setDatabaseDirectoryPath("telegram-db");
            settings.setDownloadedFilesDirectoryPath("telegram-files");
            settings.setUseFileDatabase(true);
            settings.setUseChatInfoDatabase(true);
            settings.setUseMessageDatabase(false); // Don't store message history
            
            // Create client
            Client client = ClientManager.create(settings);
            
            // Set log verbosity (1 = errors only)
            client.send(new TdApi.SetLogVerbosityLevel(1), null);
            
            log.info("Telegram client initialized successfully");
            return client;
            
        } catch (Exception e) {
            log.error("Failed to initialize Telegram client: {}", e.getMessage(), e);
            throw new RuntimeException("Telegram client initialization failed", e);
        }
    }
}