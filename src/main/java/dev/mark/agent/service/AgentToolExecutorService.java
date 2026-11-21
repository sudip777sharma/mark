package dev.mark.agent.service;

import dev.mark.agent.service.SecurityPolicyService.SecurityEvaluation;
import dev.mark.llm.dto.LlmToolCallDTO;
import dev.mark.tool.contract.*;
import dev.mark.tool.contract.PreconditionCheck.PreconditionResult;
import dev.mark.tool.contract.PostconditionCheck.PostconditionResult;
import dev.mark.tool.contract.ToolContractValidator.ValidationResult;
import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import dev.mark.tool.registry.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Service responsible for safely validating, authorizing, and executing tool calls
 * under an explicit, enforceable ToolContract execution lifecycle.
 */
@Service
public class AgentToolExecutorService {
    private static final Logger log = LoggerFactory.getLogger(AgentToolExecutorService.class);

    private final ToolRegistry toolRegistry;
    private final ToolContractRegistry contractRegistry;
    private final SecurityPolicyService securityPolicyService;
    private final AgentSafetyService legacySafetyService;

    public AgentToolExecutorService(
            ToolRegistry toolRegistry,
            ToolContractRegistry contractRegistry,
            SecurityPolicyService securityPolicyService,
            AgentSafetyService legacySafetyService) {
        this.toolRegistry = toolRegistry;
        this.contractRegistry = contractRegistry;
        this.securityPolicyService = securityPolicyService;
        this.legacySafetyService = legacySafetyService;
    }

    public AgentToolExecutorService(ToolRegistry toolRegistry, AgentSafetyService legacySafetyService) {
        this(
                toolRegistry,
                new ToolContractRegistry(toolRegistry != null ? toolRegistry.availableTools() : List.of()),
                new SecurityPolicyService(),
                legacySafetyService
        );
    }

    public ToolResultDTO execute(String taskId, LlmToolCallDTO toolCall) {
        Instant startTime = Instant.now();
        UUID taskUuid;
        try {
            taskUuid = UUID.fromString(taskId);
        } catch (Exception e) {
            taskUuid = UUID.randomUUID();
        }

        String toolName = toolCall.name();
        Map<String, Object> rawArguments = toolCall.arguments() != null ? toolCall.arguments() : Map.of();

        log.info(">>> [TOOL:CONTRACT_EXEC:START] taskId={} tool={} state={}", taskId, toolName, ToolLifecycleState.SELECTED);

        // 1. Safety Boundary Evaluation
        if (legacySafetyService.isDangerousToolCall(toolCall)) {
            log.warn("!!! [TOOL:REJECTED_LEGACY] taskId={} tool={} reason='Command rejected for safety reasons.'", taskId, toolName);
            return new ToolResultDTO(false, "Command rejected for safety reasons.", Map.of(
                    "lifecycle_state", ToolLifecycleState.AUTHORIZATION_DENIED.name(),
                    "error_code", "AUTHORIZATION_DENIED"
            ));
        }

        // 2. Resolve Tool Instance
        Optional<Tool> toolOpt = toolRegistry.getTool(toolName);
        if (toolOpt.isEmpty()) {
            log.warn("!!! [TOOL:UNKNOWN] taskId={} tool={}", taskId, toolName);
            return new ToolResultDTO(false, "Unknown tool: " + toolName, Map.of(
                    "lifecycle_state", ToolLifecycleState.UNKNOWN_TOOL.name(),
                    "error_code", "UNKNOWN_TOOL"
            ));
        }
        Tool tool = toolOpt.get();

        // 3. Resolve Contract
        ToolContract contract = contractRegistry.getContract(toolName).orElse(null);
        if (contract == null) {
            contractRegistry.registerToolWithName(toolName, tool);
            contract = contractRegistry.requireContract(toolName);
        }

        // 4. Schema & Parameter Validation
        ValidationResult valResult = ToolContractValidator.validate(contract, rawArguments);
        if (!valResult.valid()) {
            String errorMsg = "Parameter validation failed for " + toolName + ": " + String.join("; ", valResult.errors());
            log.warn("!!! [TOOL:VALIDATION_FAILED] taskId={} tool={} errors={}", taskId, toolName, valResult.errors());
            return new ToolResultDTO(false, errorMsg, Map.of(
                    "lifecycle_state", ToolLifecycleState.VALIDATION_FAILED.name(),
                    "error_code", "VALIDATION_FAILED",
                    "validation_errors", valResult.errors()
            ));
        }
        Map<String, Object> validatedArguments = valResult.normalizedArguments();

        // 5. Enterprise Security Policy Evaluation
        SecurityEvaluation secEval = securityPolicyService.evaluate(contract, validatedArguments);
        if (!secEval.allowed()) {
            log.warn("!!! [TOOL:SEC_BLOCKED] taskId={} tool={} reason='{}'", taskId, toolName, secEval.reason());
            return new ToolResultDTO(false, "Security policy violation: " + secEval.reason(), Map.of(
                    "lifecycle_state", ToolLifecycleState.AUTHORIZATION_DENIED.name(),
                    "error_code", "SECURITY_BLOCKED",
                    "reason", secEval.reason()
            ));
        }

        if (secEval.requiresApproval() && secEval.approvalRequest() != null) {
            log.info("--- [TOOL:APPROVAL_REQUIRED] taskId={} tool={} title='{}'", taskId, toolName, secEval.approvalRequest().title());
            return ToolResultDTO.intervention(
                    secEval.approvalRequest(),
                    "Action requires explicit user confirmation: " + secEval.approvalRequest().prompt(),
                    Map.of("lifecycle_state", ToolLifecycleState.AUTHORIZATION_REQUIRED.name())
            );
        }

        // 6. Precondition Checks
        ToolRequestDTO request = new ToolRequestDTO(taskUuid, toolName, validatedArguments);
        for (PreconditionCheck preCheck : contract.preconditions()) {
            PreconditionResult preResult = preCheck.check(request);
            if (!preResult.satisfied()) {
                log.warn("!!! [TOOL:PRECONDITION_FAILED] taskId={} tool={} reason='{}'", taskId, toolName, preResult.failureReason());
                Map<String, Object> meta = new HashMap<>(preResult.evidence());
                meta.put("lifecycle_state", ToolLifecycleState.PRECONDITION_FAILED.name());
                meta.put("error_code", "PRECONDITION_FAILED");
                return new ToolResultDTO(false, "Precondition failed: " + preResult.failureReason(), meta);
            }
        }

        // 7. Execution under Lifecyle State EXECUTING
        log.info("--- [TOOL:EXECUTING] taskId={} tool={} state={}", taskId, toolName, ToolLifecycleState.EXECUTING);
        ToolResultDTO rawResult;
        try {
            rawResult = tool.execute(request);
        } catch (Exception e) {
            long durationMs = java.time.Duration.between(startTime, Instant.now()).toMillis();
            log.error("!!! [TOOL:EXECUTION_FAILED] taskId={} tool={} durationMs={} error={}", taskId, toolName, durationMs, e.getMessage(), e);
            return new ToolResultDTO(false, "Tool execution failed: " + e.getMessage(), Map.of(
                    "lifecycle_state", ToolLifecycleState.EXECUTION_FAILED.name(),
                    "error_code", "EXECUTION_EXCEPTION",
                    "duration_ms", durationMs
            ));
        }

        long durationMs = java.time.Duration.between(startTime, Instant.now()).toMillis();

        if (rawResult.interventionRequest() != null) {
            return rawResult;
        }

        if (!rawResult.successful()) {
            log.warn("<<< [TOOL:DONE_UNSUCCESSFUL] taskId={} tool={} durationMs={} obs='{}'", taskId, toolName, durationMs, rawResult.observation());
            Map<String, Object> meta = new HashMap<>(rawResult.metadata());
            meta.put("lifecycle_state", ToolLifecycleState.EXECUTION_FAILED.name());
            meta.put("duration_ms", durationMs);
            return new ToolResultDTO(false, rawResult.observation(), meta);
        }

        // 8. Postcondition Verification
        if (contract.postconditionCheck() != null) {
            PostconditionResult postResult = contract.postconditionCheck().verify(request, rawResult);
            if (!postResult.verified()) {
                log.warn("!!! [TOOL:POSTCONDITION_FAILED] taskId={} tool={} reason='{}'", taskId, toolName, postResult.reason());
                Map<String, Object> meta = new HashMap<>(rawResult.metadata());
                meta.putAll(postResult.evidence());
                meta.put("lifecycle_state", ToolLifecycleState.VERIFICATION_FAILED.name());
                meta.put("verified", false);
                meta.put("error_code", "POSTCONDITION_VERIFICATION_FAILED");
                return new ToolResultDTO(false, "Postcondition verification failed: " + postResult.reason(), meta);
            }

            Map<String, Object> meta = new HashMap<>(rawResult.metadata());
            meta.putAll(postResult.evidence());
            meta.put("lifecycle_state", ToolLifecycleState.POSTCONDITIONS_VERIFIED.name());
            meta.put("verified", true);
            meta.put("duration_ms", durationMs);
            log.info("<<< [TOOL:VERIFIED] taskId={} tool={} durationMs={} obs='{}'", taskId, toolName, durationMs, rawResult.observation());
            return new ToolResultDTO(true, rawResult.observation(), meta);
        }

        Map<String, Object> meta = new HashMap<>(rawResult.metadata());
        meta.put("lifecycle_state", ToolLifecycleState.COMPLETED.name());
        meta.put("duration_ms", durationMs);
        log.info("<<< [TOOL:COMPLETED] taskId={} tool={} durationMs={} obs='{}'", taskId, toolName, durationMs, rawResult.observation());
        return new ToolResultDTO(true, rawResult.observation(), meta);
    }
}
