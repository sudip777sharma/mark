package dev.mark.tool.contract;

import dev.mark.tool.core.Tool;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry managing formal ToolContracts for all tools in the system.
 */
@Component
public class ToolContractRegistry {

    private final Map<String, ToolContract> contracts = new ConcurrentHashMap<>();

    public ToolContractRegistry(Collection<Tool> tools) {
        if (tools != null) {
            for (Tool tool : tools) {
                registerTool(tool);
            }
        }
    }

    public void registerTool(Tool tool) {
        if (tool == null) return;
        String name = tool.name();
        if (name == null || name.isBlank()) return;
        registerToolWithName(name, tool);
    }

    public void registerToolWithName(String name, Tool tool) {
        if (name == null || name.isBlank()) return;
        if (tool instanceof ContractAwareTool cat && cat.contract() != null) {
            contracts.put(name, cat.contract());
        } else {
            contracts.put(name, synthesizeContract(name, tool));
        }
    }

    public void registerContract(ToolContract contract) {
        if (contract != null && contract.toolName() != null) {
            contracts.put(contract.toolName(), contract);
        }
    }

    public Optional<ToolContract> getContract(String toolName) {
        if (toolName == null) return Optional.empty();
        return Optional.ofNullable(contracts.get(toolName));
    }

    public ToolContract requireContract(String toolName) {
        ToolContract contract = getContract(toolName).orElse(null);
        if (contract == null) {
            throw new IllegalArgumentException("No ToolContract registered for tool: " + toolName);
        }
        return contract;
    }

    public List<ToolContract> allContracts() {
        return contracts.values().stream()
                .sorted(Comparator.comparing(ToolContract::toolName))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private ToolContract synthesizeContract(String name, Tool tool) {
        String desc = "";
        try {
            desc = tool != null && tool.description() != null ? tool.description() : "";
        } catch (Exception ignored) {}

        ToolContract.Builder builder = ToolContract.builder(name)
                .description(desc)
                .category(inferCategory(name))
                .risk(inferRisk(name))
                .idempotency(inferIdempotency(name));

        Map<String, Object> schema = null;
        try {
            schema = tool != null ? tool.parameterSchema() : null;
        } catch (Exception ignored) {}

        if (schema != null && schema.containsKey("properties")) {
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            List<String> required = (List<String>) schema.getOrDefault("required", List.of());

            if (props != null) {
                for (Map.Entry<String, Object> entry : props.entrySet()) {
                    String paramName = entry.getKey();
                    Map<String, Object> propDef = entry.getValue() instanceof Map ? (Map<String, Object>) entry.getValue() : Map.of();
                    String propDesc = (String) propDef.getOrDefault("description", "");
                    String typeStr = (String) propDef.getOrDefault("type", "string");
                    boolean isReq = required != null && required.contains(paramName);

                    ToolParameterType paramType = switch (typeStr != null ? typeStr.toLowerCase() : "string") {
                        case "integer" -> ToolParameterType.INTEGER;
                        case "number" -> ToolParameterType.NUMBER;
                        case "boolean" -> ToolParameterType.BOOLEAN;
                        case "object" -> ToolParameterType.OBJECT;
                        case "array" -> ToolParameterType.ARRAY;
                        default -> ToolParameterType.STRING;
                    };

                    builder.parameter(new ToolParameterSchema(
                            paramName,
                            paramType,
                            propDesc,
                            isReq,
                            propDef.get("default"),
                            null,
                            null, null, null, null, null
                    ));
                }
            }
        } else {
            // For legacy tools or test mocks with no defined schema, permit unconstrained parameters
            builder.allowUnknownParameters(true);
        }

        return builder.build();
    }

    private String inferCategory(String name) {
        if (name == null) return "SYSTEM";
        if (name.startsWith("browser_") || name.startsWith("whatsapp_web")) return "BROWSER";
        if (name.startsWith("desktop_") || name.equals("inspect_ui") || name.equals("ocr") || name.equals("screen_recording")) return "DESKTOP";
        if (name.endsWith("_file") || name.equals("list_directory") || name.equals("search_file_content")) return "FILESYSTEM";
        if (name.equals("memorize") || name.equals("recall")) return "MEMORY";
        return "SYSTEM";
    }

    private ToolRiskClassification inferRisk(String name) {
        if (name == null) return ToolRiskClassification.READ_ONLY;
        if (name.equals("delete_file")) return ToolRiskClassification.HIGH_RISK_DESTRUCTIVE;
        if (name.equals("execute_command") || name.startsWith("desktop_automation")) return ToolRiskClassification.SYSTEM_CONSEQUENTIAL;
        if (name.startsWith("whatsapp_")) return ToolRiskClassification.EXTERNAL_COMMUNICATION;
        if (name.endsWith("_file") || name.equals("replace_in_file")) return ToolRiskClassification.WORKSPACE_MUTATION;
        if (name.equals("memorize")) return ToolRiskClassification.MEMORY_MUTATION;
        if (name.startsWith("browser_click") || name.startsWith("browser_type") || name.startsWith("browser_navigate")) return ToolRiskClassification.SYSTEM_CONSEQUENTIAL;
        return ToolRiskClassification.READ_ONLY;
    }

    private IdempotencyPolicy inferIdempotency(String name) {
        if (name == null) return IdempotencyPolicy.NATURALLY_IDEMPOTENT;
        if (name.startsWith("whatsapp_") && (name.contains("send") || name.contains("post") || name.contains("call"))) {
            return IdempotencyPolicy.NON_IDEMPOTENT;
        }
        if (name.equals("execute_command") || name.startsWith("desktop_automation")) {
            return IdempotencyPolicy.NON_IDEMPOTENT;
        }
        if (name.equals("replace_in_file")) {
            return IdempotencyPolicy.NON_IDEMPOTENT;
        }
        return IdempotencyPolicy.NATURALLY_IDEMPOTENT;
    }
}
