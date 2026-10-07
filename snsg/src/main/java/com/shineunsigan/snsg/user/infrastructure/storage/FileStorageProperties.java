package com.shineunsigan.snsg.user.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "file-storage")
public record FileStorageProperties(String directory) {
}
