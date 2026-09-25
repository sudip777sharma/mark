package dev.mark.preference;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_preferences")
public class UserPreference {
    @Id
    private String prefKey;
    private String prefValue;

    public UserPreference() {}
    public UserPreference(String prefKey, String prefValue) {
        this.prefKey = prefKey;
        this.prefValue = prefValue;
    }

    public String getPrefKey() { return prefKey; }
    public void setPrefKey(String prefKey) { this.prefKey = prefKey; }
    public String getPrefValue() { return prefValue; }
    public void setPrefValue(String prefValue) { this.prefValue = prefValue; }
}