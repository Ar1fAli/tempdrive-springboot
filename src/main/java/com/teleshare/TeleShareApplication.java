package com.teleshare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TeleShare Backend API Application
 * 
 * A Spring Boot REST API for temporary file sharing using Telegram as cloud storage.
 * Features:
 * - Direct file streaming (no local storage)
 * - 6-digit access codes for security
 * - 7-day auto-expiration
 * - Rate limiting and file validation
 * - Up to 1GB file support
 */
@SpringBootApplication
@EnableScheduling
public class TeleShareApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(TeleShareApplication.class, args);
    }
}