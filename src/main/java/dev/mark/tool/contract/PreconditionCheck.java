package dev.mark.tool.contract;

import dev.mark.tool.dto.ToolRequestDTO;
import java.util.Map;

/**
 * Functional interface for executable precondition verification before a tool runs.
 */
@FunctionalInterface
public interface PreconditionCheck {

    record PreconditionResult(boolean satisfied, String failureReason, Map<String, Object> evidence) {
        public static PreconditionResult pass() {
            return new PreconditionResult(true, null, Map.of());
        }

        public static PreconditionResult pass(Map<String, Object> evidence) {
            return new PreconditionResult(true, null, evidence);
        }

        public static PreconditionResult fail(String reason) {
            return new PreconditionResult(false, reason, Map.of());
        }

        public static PreconditionResult fail(String reason, Map<String, Object> evidence) {
            return new PreconditionResult(false, reason, evidence);
        }
    }

    PreconditionResult check(ToolRequestDTO request);
}
