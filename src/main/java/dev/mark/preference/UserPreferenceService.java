package dev.mark.preference;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserPreferenceService {
    private final UserPreferenceRepository repo;

    public UserPreferenceService(UserPreferenceRepository repo) {
        this.repo = repo;
    }

    public String getString(String key, String def) {
        return repo.findById(key).map(UserPreference::getPrefValue).orElse(def);
    }

    public int getInt(String key, int def) {
        return repo.findById(key).map(UserPreference::getPrefValue).map(Integer::parseInt).orElse(def);
    }

    public boolean getBoolean(String key, boolean def) {
        return repo.findById(key).map(UserPreference::getPrefValue).map(Boolean::parseBoolean).orElse(def);
    }

    public void setPref(String key, String value) {
        repo.save(new UserPreference(key, value));
    }

    public Map<String, String> getAllPrefs() {
        return repo.findAll().stream().collect(Collectors.toMap(UserPreference::getPrefKey, UserPreference::getPrefValue));
    }
}