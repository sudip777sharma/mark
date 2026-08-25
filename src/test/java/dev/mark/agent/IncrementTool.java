package dev.mark.agent;

import dev.mark.tool.Tool;
import dev.mark.tool.ToolRequest;
import dev.mark.tool.ToolResult;
import java.util.List;
import java.util.Map;

class IncrementTool implements Tool {
    public String name() { return "increment"; }
    public String description() { return "Increase an integer value by one."; }
    public Map<String, Object> parameterSchema() {
        return Map.of("type", "object", "properties", Map.of("value", Map.of("type", "integer")), "required", List.of("value"));
    }
    public ToolResult execute(ToolRequest request) {
        Object value = request.arguments().get("value");
        if (!(value instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())) {
            return new ToolResult(false, "increment requires an integer value", Map.of());
        }
        int incremented = number.intValue() + 1;
        return new ToolResult(true, "incremented to " + incremented, Map.of("value", incremented));
    }
}
