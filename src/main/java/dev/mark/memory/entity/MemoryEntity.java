package dev.mark.memory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_memory")
public class MemoryEntity {

    @Id
    @Column(name = "memory_key", length = 255, nullable = false)
    private String key;

    @Column(name = "memory_value", columnDefinition = "TEXT", nullable = false)
    private String value;

    @Column(nullable = false)
    private Instant updatedAt;

    public MemoryEntity() {}

    public MemoryEntity(String key, String value, Instant updatedAt) {
        this.key = key;
        this.value = value;
        this.updatedAt = updatedAt;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
