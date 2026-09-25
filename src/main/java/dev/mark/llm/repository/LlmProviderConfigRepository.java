package dev.mark.llm.repository;

import dev.mark.llm.entity.LlmProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LlmProviderConfigRepository extends JpaRepository<LlmProviderConfig, Long> {
    Optional<LlmProviderConfig> findByConfigName(String configName);
    
    Optional<LlmProviderConfig> findByIsDefaultTrue();
}
