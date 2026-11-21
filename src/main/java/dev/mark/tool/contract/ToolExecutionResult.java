package dev.mark.tool.contract;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Standardized, strongly typed result structure for every tool execution.
 * Eliminates ambiguous boolean returns and preserves empirical verification evidence.
 */
public record ToolExecutionResult(
        UUID invocationId,
        String toolName,
        ToolLifecycleState lifecycleState,
        boolean successful,
        String observation,
        Map<String, Object> output,
        String errorCode,
        String errorDetails,
        Instant startTime,
        Instant completionTime,
        long durationMs,
        ToolRiskClassification riskClassification,
        IdempotencyPolicy idempotencyPolicy,
        boolean verified,
        Map<String, Object> verificationEvidence,
        boolean retryable
) {
    public static ToolExecutionResult success(
            UUID invocationId,
            String toolName,
            String observation,
            Map<String, Object> output,
            Instant startTime,
            ToolRiskClassification risk,
            IdempotencyPolicy idempotency,
            boolean verified,
            Map<String, Object> evidence
    ) {
        Instant now = Instant.now();
        return new ToolExecutionResult(
                invocationId,
                toolName,
                verified ? ToolLifecycleState.POSTCONDITIONS_VERIFIED : ToolLifecycleState.RESULT_VALIDATED,
                true,
                observation,
                output != null ? output : Map.of(),
                null,
                null,
                startTime,
                now,
                Math.max(0, now.toEpochMilli() - startTime.toEpochMilli()),
                risk,
                idempotency,
                verified,
                evidence != null ? evidence : Map.of(),
                false
        );
    }

    public static ToolExecutionResult failure(
            UUID invocationId,
            String toolName,
            ToolLifecycleState state,
            String errorCode,
            String errorDetails,
            Instant startTime,
            ToolRiskClassification risk,
            IdempotencyPolicy idempotency,
            boolean retryable
    ) {
        Instant now = Instant.now();
        return new ToolExecutionResult(
                invocationId,
                toolName,
                state,
                false,
                errorDetails,
                Map.of(),
                errorCode,
                errorDetails,
                startTime,
                now,
                Math.max(0, now.toEpochMilli() - startTime.toEpochMilli()),
                risk,
                idempotency,
                false,
                Map.of(),
                retryable
        );
    }
}
