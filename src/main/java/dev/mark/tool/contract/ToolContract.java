package dev.mark.tool.contract;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Universal enforceable contract defining identity, schema, preconditions, execution policy,
 * and postcondition verification for a tool.
 */
public record ToolContract(
        String toolName,
        String version,
        String description,
        String capabilityCategory,
        List<ToolParameterSchema> parameters,
        ToolRiskClassification riskClassification,
        IdempotencyPolicy idempotencyPolicy,
        long timeoutMs,
        List<PreconditionCheck> preconditions,
        PostconditionCheck postconditionCheck,
        boolean allowUnknownParameters
) {
    public static Builder builder(String toolName) {
        return new Builder(toolName);
    }

    public Map<String, Object> toJsonSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();

        for (ToolParameterSchema param : parameters) {
            properties.put(param.name(), param.toJsonSchemaProperty());
            if (param.required()) {
                required.add(param.name());
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    public static class Builder {
        private final String toolName;
        private String version = "1.0.0";
        private String description = "";
        private String capabilityCategory = "GENERAL";
        private final List<ToolParameterSchema> parameters = new ArrayList<>();
        private ToolRiskClassification riskClassification = ToolRiskClassification.SAFE_INTERNAL;
        private IdempotencyPolicy idempotencyPolicy = IdempotencyPolicy.NATURALLY_IDEMPOTENT;
        private long timeoutMs = 30_000L;
        private final List<PreconditionCheck> preconditions = new ArrayList<>();
        private PostconditionCheck postconditionCheck = null;
        private boolean allowUnknownParameters = false;

        public Builder(String toolName) {
            this.toolName = toolName;
        }

        public Builder version(String version) { this.version = version; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder category(String category) { this.capabilityCategory = category; return this; }
        public Builder parameter(ToolParameterSchema param) { this.parameters.add(param); return this; }
        public Builder parameters(List<ToolParameterSchema> params) { this.parameters.addAll(params); return this; }
        public Builder risk(ToolRiskClassification risk) { this.riskClassification = risk; return this; }
        public Builder idempotency(IdempotencyPolicy idempotency) { this.idempotencyPolicy = idempotency; return this; }
        public Builder timeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; return this; }
        public Builder precondition(PreconditionCheck check) { this.preconditions.add(check); return this; }
        public Builder postcondition(PostconditionCheck check) { this.postconditionCheck = check; return this; }
        public Builder allowUnknownParameters(boolean allow) { this.allowUnknownParameters = allow; return this; }

        public ToolContract build() {
            return new ToolContract(
                    toolName,
                    version,
                    description,
                    capabilityCategory,
                    List.copyOf(parameters),
                    riskClassification,
                    idempotencyPolicy,
                    timeoutMs,
                    List.copyOf(preconditions),
                    postconditionCheck,
                    allowUnknownParameters
            );
        }
    }
}
