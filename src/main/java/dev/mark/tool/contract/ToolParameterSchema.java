package dev.mark.tool.contract;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strongly typed parameter specification for a tool contract.
 * Fully compatible with MCP and OpenAPI / JSON Schema.
 */
public record ToolParameterSchema(
        String name,
        ToolParameterType type,
        String description,
        boolean required,
        Object defaultValue,
        Set<String> allowedValues,
        Integer minLength,
        Integer maxLength,
        Double minValue,
        Double maxValue,
        String pattern
) {
    public static ToolParameterSchema requiredString(String name, String description) {
        return new ToolParameterSchema(name, ToolParameterType.STRING, description, true, null, null, 1, null, null, null, null);
    }

    public static ToolParameterSchema optionalString(String name, String description, String defaultValue) {
        return new ToolParameterSchema(name, ToolParameterType.STRING, description, false, defaultValue, null, null, null, null, null, null);
    }

    public static ToolParameterSchema requiredBoolean(String name, String description) {
        return new ToolParameterSchema(name, ToolParameterType.BOOLEAN, description, true, null, null, null, null, null, null, null);
    }

    public static ToolParameterSchema optionalBoolean(String name, String description, Boolean defaultValue) {
        return new ToolParameterSchema(name, ToolParameterType.BOOLEAN, description, false, defaultValue, null, null, null, null, null, null);
    }

    public static ToolParameterSchema requiredInt(String name, String description, int min, int max) {
        return new ToolParameterSchema(name, ToolParameterType.INTEGER, description, true, null, null, null, null, (double) min, (double) max, null);
    }

    public static ToolParameterSchema optionalInt(String name, String description, Integer defaultValue) {
        return new ToolParameterSchema(name, ToolParameterType.INTEGER, description, false, defaultValue, null, null, null, null, null, null);
    }

    public static ToolParameterSchema enumString(String name, String description, boolean required, Set<String> allowedValues) {
        return new ToolParameterSchema(name, ToolParameterType.STRING, description, required, null, allowedValues, null, null, null, null, null);
    }

    public Map<String, Object> toJsonSchemaProperty() {
        Map<String, Object> prop = new java.util.HashMap<>();
        prop.put("type", type.name().toLowerCase());
        prop.put("description", description);
        if (allowedValues != null && !allowedValues.isEmpty()) {
            prop.put("enum", List.copyOf(allowedValues));
        }
        if (defaultValue != null) {
            prop.put("default", defaultValue);
        }
        return prop;
    }
}
