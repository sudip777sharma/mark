package dev.mark.llm.service;

import dev.mark.llm.entity.LlmProviderConfig;
import dev.mark.llm.entity.ApiKeyEntry;
import dev.mark.llm.repository.LlmProviderConfigRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class LlmSettingsService {

    private static final Logger log = LoggerFactory.getLogger(LlmSettingsService.class);

    private final LlmProviderConfigRepository repository;

    @Autowired
    public LlmSettingsService(LlmProviderConfigRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void initDefaultProviders() {
        try {
            if (repository.findByConfigName("Ollama (Local)").isEmpty()) {
                LlmProviderConfig localConfig = new LlmProviderConfig(
                        "Ollama (Local)",
                        "local",
                        "qwen2.5:latest",
                        "http://localhost:11434/v1",
                        false,
                        List.of(new ApiKeyEntry("local", "ollama", true))
                );
                localConfig.setConfigured(true);
                repository.save(localConfig);
                log.info("Initialized default Local Ollama provider: http://localhost:11434/v1 (model: qwen2.5:latest)");
            }
        } catch (Exception e) {
            log.warn("Notice during Ollama default config initialization: {}", e.getMessage());
        }
    }

    public boolean hasDefaultConfig() {
        return getDefaultConfig().isPresent();
    }

    public Optional<LlmProviderConfig> getDefaultConfig() {
        Optional<LlmProviderConfig> def = repository.findByIsDefaultTrue();
        if (def.isPresent()) {
            LlmProviderConfig c = def.get();
            // If the designated default provider has all keys deactivated, auto-fallback to an active provider
            if (!"local".equalsIgnoreCase(c.getProviderType()) && getApiKeysCount(c.getId()) == 0) {
                for (LlmProviderConfig alt : repository.findAll()) {
                    if (Boolean.TRUE.equals(alt.isConfigured())) {
                        if ("local".equalsIgnoreCase(alt.getProviderType()) || getApiKeysCount(alt.getId()) > 0) {
                            log.info("Primary default '{}' has no active keys; using active alternative '{}'", c.getConfigName(), alt.getConfigName());
                            return Optional.of(alt);
                        }
                    }
                }
            }
            return def;
        }

        // If no explicit default, pick first configured provider with active keys or local
        for (LlmProviderConfig alt : repository.findAll()) {
            if (Boolean.TRUE.equals(alt.isConfigured())) {
                if ("local".equalsIgnoreCase(alt.getProviderType()) || getApiKeysCount(alt.getId()) > 0) {
                    return Optional.of(alt);
                }
            }
        }

        return Optional.empty();
    }

    public List<LlmProviderConfig> getAvailableFallbackConfigs(Long currentConfigId) {
        return repository.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.isConfigured()))
                .filter(c -> currentConfigId == null || !currentConfigId.equals(c.getId()))
                .filter(c -> "local".equalsIgnoreCase(c.getProviderType()) || getApiKeysCount(c.getId()) > 0)
                .sorted((a, b) -> {
                    boolean aLocal = "local".equalsIgnoreCase(a.getProviderType());
                    boolean bLocal = "local".equalsIgnoreCase(b.getProviderType());
                    if (aLocal && !bLocal) return 1;
                    if (!aLocal && bLocal) return -1;
                    return 0;
                })
                .toList();
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
                    if ("local".equalsIgnoreCase(config.getProviderType())) {
                        return "ollama";
                    }
                    List<ApiKeyEntry> allKeys = config.getApiKeys();
                    if (allKeys == null || allKeys.isEmpty()) return null;
                    
                    List<ApiKeyEntry> activeKeys = allKeys.stream()
                            .filter(k -> k.getIsActive() == null || k.getIsActive())
                            .toList();
                            
                    if (activeKeys.isEmpty()) return null; // All keys exhausted

                    int index = keyIndices.getOrDefault(configId, 0);
                    ApiKeyEntry entry = activeKeys.get(index % activeKeys.size());
                    keyIndices.put(configId, (index + 1) % activeKeys.size());
                    return entry != null ? entry.getKeyValue() : null;
                })
                .orElse(null);
    }

    public int getApiKeysCount(Long configId) {
        if (configId == null) return 0;
        return repository.findById(configId)
                .map(config -> {
                    if ("local".equalsIgnoreCase(config.getProviderType())) {
                        return 1;
                    }
                    if (config.getApiKeys() == null) return 0;
                    return (int) config.getApiKeys().stream()
                            .filter(k -> k.getIsActive() == null || k.getIsActive())
                            .count();
                })
                .orElse(0);
    }

    @Transactional
    public void markKeyAsInactive(Long configId, String keyValue) {
        if (configId == null || keyValue == null) return;
        repository.findById(configId).ifPresent(config -> {
            if (config.getApiKeys() != null) {
                boolean modified = false;
                for (ApiKeyEntry key : config.getApiKeys()) {
                    if (keyValue.equals(key.getKeyValue()) && (key.getIsActive() == null || key.getIsActive())) {
                        key.setIsActive(false);
                        modified = true;
                    }
                }
                if (modified) {
                    repository.save(config);
                }
            }
        });
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
    public LlmProviderConfig saveConfig(Long id, String configName, String providerType, String activeModel, String baseUrl, List<ApiKeyEntry> apiKeys, boolean isDefault, boolean isConfigured) {
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
        config.setConfigured(isConfigured);
        
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
