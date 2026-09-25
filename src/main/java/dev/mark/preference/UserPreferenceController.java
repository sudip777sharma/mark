package dev.mark.preference;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/preferences")
public class UserPreferenceController {
    private final UserPreferenceService service;

    public UserPreferenceController(UserPreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, String> getAll() {
        return service.getAllPrefs();
    }

    @PostMapping
    public void savePrefs(@RequestBody Map<String, String> prefs) {
        prefs.forEach(service::setPref);
    }
}