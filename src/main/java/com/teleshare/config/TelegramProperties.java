package com.teleshare.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;

/**
 * Configuration properties for Telegram integration
 */
@Component
@ConfigurationProperties(prefix = "telegram")
@Data
@Validated
public class TelegramProperties {
    
    /**
     * Telegram API ID (from https://my.telegram.org)
     */
    @NotBlank(message = "Telegram API ID is required")
    private String apiId;
    
    /**
     * Telegram API Hash (from https://my.telegram.org)
     */
    @NotBlank(message = "Telegram API Hash is required")
    private String apiHash;
    
    /**
     * Phone number associated with Telegram account
     */
    @NotBlank(message = "Telegram phone number is required")
    private String phoneNumber;
    
    /**
     * Telegram channel ID where files will be stored
     */
    @NotBlank(message = "Telegram storage channel ID is required")
    private String storageChannelId;
}