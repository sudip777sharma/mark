package dev.mark.tool;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mark.filesystem")
public record FileSystemProperties(String basePath) {
    public FileSystemProperties {
        if (basePath == null || basePath.isBlank()) basePath = "./mark-workspace";
    }
}
