package dev.mark.llm.entity;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "llm_provider_config")
public class LlmProviderConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String configName; // e.g. "Gemini Flash"

    @Column(nullable = false)
    private String providerType; // e.g. "gemini", "local", "groq"

    private String activeModel;

    private String baseUrl;

    private boolean isDefault;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<ApiKeyEntry> apiKeys = new ArrayList<>();

    // For round-robin tracking (not persisted)
    private transient int currentKeyIndex = 0;

    public LlmProviderConfig() {
    }

    public LlmProviderConfig(String configName, String providerType, String activeModel, String baseUrl, boolean isDefault, List<ApiKeyEntry> apiKeys) {
        this.configName = configName;
        this.providerType = providerType;
        this.activeModel = activeModel;
        this.baseUrl = baseUrl;
        this.isDefault = isDefault;
        if (apiKeys != null) {
            this.apiKeys.addAll(apiKeys);
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getActiveModel() {
        return activeModel;
    }

    public void setActiveModel(String activeModel) {
        this.activeModel = activeModel;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public List<ApiKeyEntry> getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(List<ApiKeyEntry> apiKeys) {
        this.apiKeys = apiKeys;
    }
}
