package dev.mark.tool.contract;

import java.util.*;

/**
 * Strict schema-driven validator for tool arguments.
 * Ensures the LLM is never trusted as the final authority for parameter correctness.
 */
public class ToolContractValidator {

    public record ValidationResult(boolean valid, List<String> errors, Map<String, Object> normalizedArguments) {
        public static ValidationResult success(Map<String, Object> normalized) {
            return new ValidationResult(true, List.of(), normalized);
        }

        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors, Map.of());
        }

        public static ValidationResult failure(String error) {
            return new ValidationResult(false, List.of(error), Map.of());
        }
    }

    public static ValidationResult validate(ToolContract contract, Map<String, Object> rawArguments) {
        Map<String, Object> raw = rawArguments != null ? rawArguments : Map.of();
        List<String> errors = new ArrayList<>();
        Map<String, Object> normalized = new HashMap<>();

        Map<String, ToolParameterSchema> paramMap = new HashMap<>();
        for (ToolParameterSchema param : contract.parameters()) {
            paramMap.put(param.name(), param);
        }

        // 1. Check for unexpected parameters unless explicitly permitted
        if (!contract.allowUnknownParameters()) {
            for (String providedKey : raw.keySet()) {
                // Ignore benign meta-parameters that LLMs may output
                if (providedKey.equals("thought") || providedKey.equals("rationale") || providedKey.equals("_call_id")) {
                    continue;
                }
                if (!paramMap.containsKey(providedKey)) {
                    errors.add("Unexpected argument '" + providedKey + "' not defined in contract for " + contract.toolName());
                }
            }
        } else {
            // For contracts allowing unknown parameters, preserve them in normalized output
            normalized.putAll(raw);
        }

        // 2. Validate declared parameters
        for (ToolParameterSchema schema : contract.parameters()) {
            String name = schema.name();
            Object value = raw.get(name);

            if (value == null || (value instanceof String s && s.isBlank() && schema.required())) {
                if (schema.required()) {
                    // Check default value if provided
                    if (schema.defaultValue() != null) {
                        normalized.put(name, schema.defaultValue());
                    } else {
                        errors.add("Missing required parameter '" + name + "' for tool " + contract.toolName());
                    }
                } else if (schema.defaultValue() != null) {
                    normalized.put(name, schema.defaultValue());
                }
                continue;
            }

            // Type check
            if (!schema.type().matches(value)) {
                errors.add("Parameter '" + name + "' must be of type " + schema.type() + ", but got " + value.getClass().getSimpleName());
                continue;
            }

            // String constraints
            if (value instanceof String strVal) {
                String trimmed = strVal.trim();
                if (schema.minLength() != null && trimmed.length() < schema.minLength()) {
                    errors.add("Parameter '" + name + "' length must be at least " + schema.minLength() + " characters");
                }
                if (schema.maxLength() != null && trimmed.length() > schema.maxLength()) {
                    errors.add("Parameter '" + name + "' length exceeds maximum of " + schema.maxLength() + " characters");
                }
                if (schema.allowedValues() != null && !schema.allowedValues().isEmpty()) {
                    if (!schema.allowedValues().contains(trimmed)) {
                        errors.add("Parameter '" + name + "' value '" + trimmed + "' is not in allowed values: " + schema.allowedValues());
                    }
                }
                normalized.put(name, trimmed);
            } else if (value instanceof Number numVal) {
                double doubleVal = numVal.doubleValue();
                if (schema.minValue() != null && doubleVal < schema.minValue()) {
                    errors.add("Parameter '" + name + "' value " + doubleVal + " is less than minimum allowed " + schema.minValue());
                }
                if (schema.maxValue() != null && doubleVal > schema.maxValue()) {
                    errors.add("Parameter '" + name + "' value " + doubleVal + " exceeds maximum allowed " + schema.maxValue());
                }
                normalized.put(name, value);
            } else {
                normalized.put(name, value);
            }
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }
        return ValidationResult.success(normalized);
    }
}
