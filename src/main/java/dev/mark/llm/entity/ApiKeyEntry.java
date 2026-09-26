package dev.mark.llm.entity;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Column;

@Embeddable
public class ApiKeyEntry {

    private String keyName;

    @Column(nullable = false, length = 1000)
    private String keyValue;

    public ApiKeyEntry() {}

    public ApiKeyEntry(String keyName, String keyValue) {
        this.keyName = keyName;
        this.keyValue = keyValue;
    }

    public String getKeyName() { return keyName; }
    public void setKeyName(String keyName) { this.keyName = keyName; }
    public String getKeyValue() { return keyValue; }
    public void setKeyValue(String keyValue) { this.keyValue = keyValue; }
}
