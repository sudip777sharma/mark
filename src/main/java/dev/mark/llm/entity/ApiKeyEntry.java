package dev.mark.llm.entity;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Column;

@Embeddable
public class ApiKeyEntry {

    private String keyName;

    @Column(nullable = false, length = 1000)
    private String keyValue;

    @Column(name = "is_active", columnDefinition = "boolean default true")
    private Boolean isActive = true;

    public ApiKeyEntry() {}

    public ApiKeyEntry(String keyName, String keyValue) {
        this.keyName = keyName;
        this.keyValue = keyValue;
        this.isActive = true;
    }

    public ApiKeyEntry(String keyName, String keyValue, Boolean isActive) {
        this.keyName = keyName;
        this.keyValue = keyValue;
        this.isActive = isActive;
    }

    public String getKeyName() { return keyName; }
    public void setKeyName(String keyName) { this.keyName = keyName; }
    public String getKeyValue() { return keyValue; }
    public void setKeyValue(String keyValue) { this.keyValue = keyValue; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
