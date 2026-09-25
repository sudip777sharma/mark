package dev.mark.llm.service;

import dev.mark.llm.entity.LlmProviderConfig;
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

    public Optional<LlmProviderConfig> getConfig(String configName) {
        return repository.findByConfigName(configName);
    }

    private final java.util.Map<String, Integer> keyIndices = new java.util.concurrent.ConcurrentHashMap<>();

    public synchronized String getNextApiKey(String configName) {
        return repository.findByConfigName(configName)
                .map(config -> {
                    List<String> keys = config.getApiKeys();
                    if (keys == null || keys.isEmpty()) return null;
                    int index = keyIndices.getOrDefault(configName, 0);
                    String key = keys.get(index % keys.size());
                    keyIndices.put(configName, (index + 1) % keys.size());
                    return key;
                })
                .orElse(null);
    }

    public int getApiKeysCount(String configName) {
        return repository.findByConfigName(configName)
                .map(config -> config.getApiKeys() != null ? config.getApiKeys().size() : 0)
                .orElse(0);
    }

    public List<LlmProviderConfig> getAllConfigs() {
        return repository.findAll();
    }

    @Transactional
    public void deleteConfig(String configName) {
        repository.findByConfigName(configName).ifPresent(repository::delete);
    }

    @Transactional
    public LlmProviderConfig saveConfig(String configName, String providerType, String activeModel, String baseUrl, List<String> apiKeys, boolean isDefault) {
        LlmProviderConfig config = repository.findByConfigName(configName)
                .orElse(new LlmProviderConfig());

        config.setConfigName(configName);
        config.setProviderType(providerType);
        config.setActiveModel(activeModel);
        config.setBaseUrl(baseUrl);
        config.setApiKeys(apiKeys);
        
        if (isDefault) {
            // Deactivate all others
            repository.findAll().forEach(c -> {
                if (!c.getConfigName().equals(configName) && c.isDefault()) {
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
