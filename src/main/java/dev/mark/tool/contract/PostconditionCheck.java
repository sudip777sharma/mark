package dev.mark.tool.contract;

import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import java.util.Map;

/**
 * Functional interface for independent postcondition verification after a tool execution.
 */
@FunctionalInterface
public interface PostconditionCheck {

    record PostconditionResult(boolean verified, String reason, Map<String, Object> evidence) {
        public static PostconditionResult pass(String evidenceSummary, Map<String, Object> evidence) {
            return new PostconditionResult(true, evidenceSummary, evidence);
        }

        public static PostconditionResult fail(String failureReason, Map<String, Object> evidence) {
            return new PostconditionResult(false, failureReason, evidence);
        }

        public static PostconditionResult notApplicable() {
            return new PostconditionResult(true, "Not applicable for read-only tool", Map.of("verified", true));
        }
    }

    PostconditionResult verify(ToolRequestDTO request, ToolResultDTO executionResult);
}
