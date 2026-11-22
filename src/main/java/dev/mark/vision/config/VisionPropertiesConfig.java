package dev.mark.vision.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for MARK's dual-tier local vision engine.
 */
@Configuration
@ConfigurationProperties(prefix = "mark.vision")
public class VisionPropertiesConfig {

    private String primaryModel = "mirage";
    private String secondaryModel = "qwen3-vl";
    private MirageProperties mirage = new MirageProperties();
    private QwenProperties qwen = new QwenProperties();

    public String getPrimaryModel() {
        return primaryModel;
    }

    public void setPrimaryModel(String primaryModel) {
        this.primaryModel = primaryModel;
    }

    public String getSecondaryModel() {
        return secondaryModel;
    }

    public void setSecondaryModel(String secondaryModel) {
        this.secondaryModel = secondaryModel;
    }

    public MirageProperties getMirage() {
        return mirage;
    }

    public void setMirage(MirageProperties mirage) {
        this.mirage = mirage;
    }

    public QwenProperties getQwen() {
        return qwen;
    }

    public void setQwen(QwenProperties qwen) {
        this.qwen = qwen;
    }

    public static class MirageProperties {
        private String baseUrl = "http://localhost:8088/v1";
        private String model = "mirage";
        private int timeoutMs = 3000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    public static class QwenProperties {
        private String baseUrl = "http://localhost:11434/v1";
        private String model = "qwen3-vl:2b";
        private int timeoutMs = 35000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }
}
