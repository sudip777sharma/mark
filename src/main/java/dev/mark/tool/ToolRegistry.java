package dev.mark.tool;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ToolRegistry {
    private final Map<String, Tool> tools;
    public ToolRegistry(Collection<Tool> tools) { this.tools = tools.stream().collect(Collectors.toUnmodifiableMap(Tool::name, Function.identity())); }
    public Tool require(String name) { Tool tool = tools.get(name); if (tool == null) throw new IllegalArgumentException("Unknown tool: " + name); return tool; }
    public List<Tool> availableTools() { return tools.values().stream().sorted(java.util.Comparator.comparing(Tool::name)).toList(); }
}
