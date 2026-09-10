package dev.mark.tool.registry;

import dev.mark.tool.core.Tool;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * - What it does: Acts as an in-memory registry for managing and organizing available Tool instances.
 * - Why it is useful: Centralizes tool lookup, preventing redundant instantiations and providing unified access.
 * - Flow in application: Injected into execution services that require specific tools to perform operations.
 * - Methods and variables: Uses an immutable tools map for storage; require() fetches mandatory tools, getTool() retrieves optional ones, and availableTools() lists sorted capabilities.
 * - Application logic: Automatically aggregates all Spring-managed Tool beans upon startup, integrating a dynamic plugin architecture into the core execution flow.
 */

/**
 * - What it does: Acts as an in-memory registry for managing and organizing available Tool instances.
 * - Why it is useful: Centralizes tool lookup, preventing redundant instantiations and ensuring thread-safe access.
 * - Flow in application: Acts as a central lookup component injected into execution services that require specific tools.
 * - Methods and variables: Uses an immutable tools map to store components; require() fetches mandatory tools, getTool() safely retrieves optional tools, and availableTools() lists sorted capabilities.
 * - Application logic: Automatically aggregates all Spring-managed Tool beans upon startup, integrating dynamic plugin-style architecture into the core execution flow.
 */

@Component
public class ToolRegistry {
    private final Map<String, Tool> tools;
    public ToolRegistry(Collection<Tool> tools) { this.tools = tools.stream().collect(Collectors.toUnmodifiableMap(Tool::name, Function.identity())); }
    public Tool require(String name) { Tool tool = tools.get(name); if (tool == null) throw new IllegalArgumentException("Unknown tool: " + name); return tool; }
    public java.util.Optional<Tool> getTool(String name) { return java.util.Optional.ofNullable(tools.get(name)); }
    public List<Tool> availableTools() { return tools.values().stream().sorted(java.util.Comparator.comparing(Tool::name)).toList(); }
}
