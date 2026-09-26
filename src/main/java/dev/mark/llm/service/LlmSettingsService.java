package dev.mark.llm.service;

import dev.mark.llm.entity.LlmProviderConfig;
import dev.mark.llm.entity.ApiKeyEntry;
import dev.mark.llm.repository.LlmProviderConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class LlmSettingsService {

    private final LlmProviderConfigRepository repository;

    @Autowired
    public LlmSettingsService(LlmProviderConfigRepository repository) {
        this.repository = repository;
    }

    public boolean hasDefaultConfig() {
        return repository.findByIsDefaultTrue().isPresent();
    }

    public Optional<LlmProviderConfig> getDefaultConfig() {
        return repository.findByIsDefaultTrue();
    }

    public Optional<LlmProviderConfig> getConfig(Long id) {
        if (id == null) return Optional.empty();
        return repository.findById(id);
    }

    public Optional<LlmProviderConfig> getConfig(String configName) {
        return repository.findByConfigName(configName);
    }

    private final java.util.Map<Long, Integer> keyIndices = new java.util.concurrent.ConcurrentHashMap<>();

    public synchronized String getNextApiKey(Long configId) {
        if (configId == null) return null;
        return repository.findById(configId)
                .map(config -> {
                    List<ApiKeyEntry> keys = config.getApiKeys();
                    if (keys == null || keys.isEmpty()) return null;
                    int index = keyIndices.getOrDefault(configId, 0);
                    ApiKeyEntry entry = keys.get(index % keys.size());
                    keyIndices.put(configId, (index + 1) % keys.size());
                    return entry != null ? entry.getKeyValue() : null;
                })
                .orElse(null);
    }

    public int getApiKeysCount(Long configId) {
        if (configId == null) return 0;
        return repository.findById(configId)
                .map(config -> config.getApiKeys() != null ? config.getApiKeys().size() : 0)
                .orElse(0);
    }

    public List<LlmProviderConfig> getAllConfigs() {
        return repository.findAll();
    }

    @Transactional
    public void deleteConfig(Long id) {
        if (id != null) {
            repository.findById(id).ifPresent(repository::delete);
        }
    }

    @Transactional
    public LlmProviderConfig saveConfig(Long id, String configName, String providerType, String activeModel, String baseUrl, List<ApiKeyEntry> apiKeys, boolean isDefault) {
        LlmProviderConfig config;
        if (id != null) {
            config = repository.findById(id).orElse(new LlmProviderConfig());
        } else {
            config = repository.findByConfigName(configName).orElse(new LlmProviderConfig());
        }

        config.setConfigName(configName);
        config.setProviderType(providerType);
        config.setActiveModel(activeModel);
        config.setBaseUrl(baseUrl);
        config.setApiKeys(apiKeys);
        
        if (isDefault) {
            // Deactivate all others
            repository.findAll().forEach(c -> {
                if (!c.getId().equals(config.getId()) && c.isDefault()) {
                    c.setDefault(false);
                    repository.save(c);
                }
            });
        } else {
            // If it's the first config, make it default regardless
            if (repository.count() == 0 || (config.getId() == null && repository.count() == 0)) {
                isDefault = true;
            }
        }
        
        config.setDefault(isDefault);
        return repository.save(config);
    }
}
