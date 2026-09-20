package dev.mark.tool.impl.memory;

import dev.mark.memory.service.MemoryService;
import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.List;

@Component
public class MemorizeTool implements Tool {

    private final MemoryService memoryService;

    public MemorizeTool(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Override
    public String name() {
        return "memorize";
    }

    @Override
    public String description() {
        return "Stores a piece of information in the long-term memory for future retrieval. Use this to remember user preferences, facts, or context that should persist across sessions.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "key", Map.of("type", "string", "description", "A unique key/topic for this memory."),
                        "value", Map.of("type", "string", "description", "The detailed information to remember.")
                ),
                "required", List.of("key", "value")
        );
    }

    @Override
    public ToolResultDTO execute(ToolRequestDTO request) {
        String key = (String) request.arguments().get("key");
        String value = (String) request.arguments().get("value");
        if (key == null || value == null) {
            return new ToolResultDTO(false, "Error: Both key and value must be provided.", Map.of());
        }
        memoryService.store(key, value);
        return ToolResultDTO.success("Successfully memorized: " + key);
    }
}
