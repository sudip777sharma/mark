package dev.mark.tool.contract;

public enum ToolParameterType {
    STRING,
    INTEGER,
    NUMBER,
    BOOLEAN,
    OBJECT,
    ARRAY;

    public boolean matches(Object value) {
        if (value == null) return false;
        return switch (this) {
            case STRING -> value instanceof String;
            case INTEGER -> value instanceof Integer || value instanceof Long || (value instanceof Number n && n.doubleValue() == n.longValue());
            case NUMBER -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case OBJECT -> value instanceof java.util.Map;
            case ARRAY -> value instanceof java.util.List || value.getClass().isArray();
        };
    }
}
