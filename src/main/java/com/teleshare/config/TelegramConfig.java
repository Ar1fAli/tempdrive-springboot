package com.teleshare.config;

import it.tdlight.Init;
import it.tdlight.Log;
import it.tdlight.Slf4JLogMessageHandler;
import it.tdlight.client.APIToken;
import it.tdlight.client.AuthenticationSupplier;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.client.SimpleTelegramClientFactory;
import it.tdlight.client.TDLibSettings;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Telegram client configuration
 */
@Configuration
@Slf4j
public class TelegramConfig {

  @Autowired
  private TelegramProperties telegramProperties;

  @Bean
  public SimpleTelegramClient telegramClient() {
    try {
      // Initialize native libraries
      Init.init();

      // Set log level
      Log.setLogMessageHandler(1, new Slf4JLogMessageHandler());

      // Create API token
      APIToken apiToken = new APIToken(
          Integer.parseInt(telegramProperties.getApiId()),
          telegramProperties.getApiHash());

      // Configure TDLib settings
      TDLibSettings settings = TDLibSettings.create(apiToken);
      Path sessionPath = Paths.get("telegram-session");
      settings.setDatabaseDirectoryPath(sessionPath.resolve("data"));
      settings.setDownloadedFilesDirectoryPath(sessionPath.resolve("downloads"));

      // Create authentication supplier (interactive console login)
      AuthenticationSupplier authenticationSupplier = AuthenticationSupplier.consoleLogin();

      // Create and build the client
      SimpleTelegramClientFactory factory = new SimpleTelegramClientFactory();
      SimpleTelegramClient client = factory.builder(settings)
          .build(authenticationSupplier);

      // Set log verbosity level
      client.send(new TdApi.SetLogVerbosityLevel(1), result -> {
        if (result.isError()) {
          log.error("Failed to set log verbosity: {}", result.getError().message);
        }
      });

      log.info("Telegram client initialized successfully");
      return client;

    } catch (Exception e) {
      log.error("Failed to initialize Telegram client: {}", e.getMessage(), e);
      throw new RuntimeException("Telegram client initialization failed", e);
    }
  }
}
