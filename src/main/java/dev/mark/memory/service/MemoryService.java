package dev.mark.memory.service;

import dev.mark.memory.entity.MemoryEntity;
import dev.mark.memory.repository.MemoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MemoryService {

    private final MemoryRepository memoryRepository;

    public MemoryService(MemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    @Transactional
    public void store(String key, String value) {
        MemoryEntity entity = new MemoryEntity(key, value, Instant.now());
        memoryRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public Optional<String> retrieve(String key) {
        return memoryRepository.findById(key).map(MemoryEntity::getValue);
    }
    
    @Transactional(readOnly = true)
    public Map<String, String> retrieveAll() {
        return memoryRepository.findAll().stream()
                .collect(Collectors.toMap(MemoryEntity::getKey, MemoryEntity::getValue));
    }
}
