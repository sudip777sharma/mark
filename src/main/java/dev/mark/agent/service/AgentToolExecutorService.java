package dev.mark.agent.service;

import dev.mark.llm.dto.LlmToolCallDTO;
import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import dev.mark.tool.registry.ToolRegistry;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service responsible for safely validating and executing tool calls requested by the LLM.
 *
 * - **What it does**: Bridges LLM tool execution requests with concrete tool implementations.
 * - **Why it is useful**: Centralizes error handling and prevents malicious or unauthorized tool usage.
 * - **Application flow**: Acts as a secure gatekeeper between the agent orchestrator and the underlying tool execution layer.
 * - **Methods and variables**:
 *   - toolRegistry: Resolves and retrieves registered tool instances by name.
 *   - safetyService: Evaluates incoming tool requests for potential security risks.
 *   - execute(): Validates safety, verifies tool existence, and invokes the tool safely.
 * - **Logic**: Intercepts raw tool calls, blocks dangerous operations, fetches the target tool, and executes it while gracefully handling exceptions into standardized results.
 */

/**
 * Service responsible for validating and executing tool calls requested by the LLM.
 *
 * - **What it does**: Bridges LLM tool execution requests with concrete tool implementations.
 * - **Why it is useful**: Centralizes error handling and prevents malicious tool usage.
 * - **Application flow**: Acts as a secure gatekeeper between the agent orchestrator and the tool execution layer.
 * - **Methods and variables**:
 *   - toolRegistry: Retrieves registered tool instances by name.
 *   - safetyService: Evaluates tool requests for security risks.
 *   - execute(): Validates safety, verifies tool existence, and invokes the tool safely.
 * - **Logic**: Intercepts raw tool calls, blocks dangerous operations, fetches the target tool, and executes it while gracefully handling exceptions.
 */

/**
 * Service responsible for validating and executing tool calls requested by the LLM.
 *
 * - **Purpose**: Bridges LLM tool requests with concrete implementations while enforcing safety checks.
 * - **Utility**: Centralizes error handling, prevents malicious tool usage, and standardizes execution results.
 * - **Application Flow**: Acts as a secure gatekeeper between the agent orchestrator and the tool execution layer.
 * - **Components**:
 *   - toolRegistry: Retrieves registered tool instances by name.
 *   - safetyService: Evaluates tool requests for potential security risks.
 *   - execute(): Validates safety, verifies tool existence, and invokes the tool safely.
 * - **Logic**: Intercepts raw tool calls, blocks dangerous operations, fetches the target tool, and executes it while gracefully handling exceptions.
 */

/**
 * Service responsible for safely validating and executing tool calls requested by the LLM.
 *
 * - **Purpose**: Bridges LLM tool execution requests with actual tool implementations while enforcing safety checks.
 * - **Utility**: Prevents malicious tool usage and centralizes error handling during dynamic tool invocation.
 * - **Application Flow**: Sits between the agent orchestrator and the tool execution layer, acting as a secure gatekeeper.
 * - **Components**:
 */

/**
 * Service responsible for validating and executing tool calls requested by the LLM.
 *
 * - **What it does**: Bridges LLM tool execution requests with concrete tool implementations.
 * - **Why it is useful**: Centralizes error handling and prevents malicious tool usage.
 * - **Application flow**: Acts as a secure gatekeeper between the agent orchestrator and the tool execution layer.
 * - **Methods and variables**:
 *   - toolRegistry: Retrieves registered tool instances by name.
 *   - safetyService: Evaluates tool requests for security risks.
 *   - execute(): Validates safety, verifies tool existence, and invokes the tool safely.
 * - **Logic**: Intercepts raw tool calls, blocks dangerous operations, fetches the target tool, and executes it while gracefully handling exceptions.
 */

/**
 * Service responsible for validating and executing tool calls requested by the LLM.
 *
 * - **Purpose**: Bridges LLM tool requests with concrete implementations while enforcing safety checks.
 * - **Utility**: Centralizes error handling, prevents malicious tool usage, and standardizes execution results.
 * - **Application Flow**: Acts as a secure gatekeeper between the agent orchestrator and the tool execution layer.
 * - **Components**:
 *   - toolRegistry: Retrieves registered tool instances by name.
 *   - safetyService: Evaluates tool requests for potential security risks.
 *   - execute(): Validates safety, verifies tool existence, and invokes the tool safely.
 * - **Logic**: Intercepts raw tool calls, blocks dangerous operations, fetches the target tool, and executes it while gracefully handling exceptions.
 */

/**
 * Service responsible for safely validating and executing tool calls requested by the LLM.
 *
 * - **Purpose**: Bridges LLM tool execution requests with actual tool implementations while enforcing safety checks.
 * - **Utility**: Prevents malicious tool usage and centralizes error handling during dynamic tool invocation.
 * - **Application Flow**: Sits between the agent orchestrator and the tool execution layer, acting as a secure gatekeeper.
 * - **Components**:
 *   - toolRegistry: Retrieves registered tool instances by name.
 *   - safetyService: Evaluates tool calls for potential security risks.
 *   - execute(): Validates safety, checks tool existence, and executes the request, returning a standardized result.
 * - **Logic**: Intercepts raw tool call DTOs, blocks dangerous operations, fetches the target tool, and safely executes it while catching unexpected failures.
 */

@Service
public class AgentToolExecutorService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AgentToolExecutorService.class);

    private final ToolRegistry toolRegistry;
    private final AgentSafetyService safetyService;

    public AgentToolExecutorService(ToolRegistry toolRegistry, AgentSafetyService safetyService) {
        this.toolRegistry = toolRegistry;
        this.safetyService = safetyService;
    }

    public ToolResultDTO execute(String taskId, LlmToolCallDTO toolCall) {
        long startTime = System.currentTimeMillis();
        log.info(">>> [TOOL:EXEC] taskId={} tool={} args={}", taskId, toolCall.name(), toolCall.arguments());

        if (safetyService.isDangerousToolCall(toolCall)) {
            log.warn("!!! [TOOL:REJECTED] taskId={} tool={} reason='Command rejected for safety reasons.'", taskId, toolCall.name());
            return new ToolResultDTO(false, "Command rejected for safety reasons.", Map.of());
        }

        Optional<Tool> toolOpt = toolRegistry.getTool(toolCall.name());
        if (toolOpt.isEmpty()) {
            log.warn("!!! [TOOL:UNKNOWN] taskId={} tool={}", taskId, toolCall.name());
            return new ToolResultDTO(false, "Unknown tool: " + toolCall.name(), Map.of());
        }

        Tool tool = toolOpt.get();
        ToolRequestDTO toolRequest = new ToolRequestDTO(UUID.fromString(taskId), toolCall.name(), toolCall.arguments());
        try {
            ToolResultDTO result = tool.execute(toolRequest);
            long duration = System.currentTimeMillis() - startTime;
            String obsPreview = result.observation() != null ? result.observation() : "none";
            log.info("<<< [TOOL:DONE] taskId={} tool={} success={} durationMs={} observation='{}'",
                taskId, toolCall.name(), result.successful(), duration, obsPreview);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("!!! [TOOL:FAILED] taskId={} tool={} durationMs={} error={}", taskId, toolCall.name(), duration, e.getMessage(), e);
            return new ToolResultDTO(false, "Tool execution failed: " + e.getMessage(), Map.of());
        }
    }
}
