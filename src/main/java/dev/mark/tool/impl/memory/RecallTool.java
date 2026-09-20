package dev.mark.tool.impl.memory;

import dev.mark.memory.service.MemoryService;
import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.List;

@Component
public class RecallTool implements Tool {

    private final MemoryService memoryService;

    public RecallTool(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Override
    public String name() {
        return "recall";
    }

    @Override
    public String description() {
        return "Retrieves a piece of information from the long-term memory by key.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "key", Map.of("type", "string", "description", "The unique key/topic to recall.")
                ),
                "required", List.of("key")
        );
    }

    @Override
    public ToolResultDTO execute(ToolRequestDTO request) {
        String key = (String) request.arguments().get("key");
        if (key == null) {
            return new ToolResultDTO(false, "Error: key must be provided.", Map.of());
        }
        String value = memoryService.retrieve(key).orElse("Memory not found for key: " + key);
        return ToolResultDTO.success(value);
    }
}
