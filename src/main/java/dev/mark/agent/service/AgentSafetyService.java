package dev.mark.agent.service;


import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.*;
import dev.mark.llm.dto.*;
import dev.mark.llm.dto.LlmToolCallDTO;
import org.springframework.stereotype.Service;
import java.util.Map;

/**
 * - Evaluates LLM tool calls to detect potentially harmful operations before execution.
 * - Protects the system from destructive actions, preventing accidental data loss or unauthorized modifications.
 * - Acts as a security interception layer between LLM request handling and the tool execution engine.
 * - Utilizes `isDangerousToolCall` to validate tool requests and `isDangerousCommand` to scan command strings against blacklisted operations.
 * - Integrates into the application pipeline by intercepting and blocking unsafe commands before they reach the execution phase.
 */

/**
 * - Evaluates LLM tool calls to detect potentially harmful operations before execution.
 * - Protects the system from destructive actions, preventing accidental data loss or unauthorized modifications.
 * - Acts as an interception layer between the LLM request handler and the tool execution engine.
 * - Uses `isDangerousToolCall` to validate tool requests and `isDangerousCommand` to scan command strings for blacklisted operations.
 * - Integrates into the security pipeline by blocking unsafe commands, ensuring that only validated tool calls proceed to execution.
 */

/**
 * - Evaluates LLM tool calls to detect potentially harmful operations before execution.
 * - Protects the system from destructive actions, preventing accidental data loss or unauthorized modifications.
 * - Acts as a security interception layer between LLM request handling and the tool execution engine.
 * - Uses `isDangerousToolCall` to validate tool requests and `isDangerousCommand` to scan command strings for blacklisted operations.
 * - Integrates into the application execution pipeline by intercepting and blocking unsafe commands before they reach the execution phase.
 */

/**
 * - Evaluates LLM tool calls to detect potentially harmful operations before execution.
 * - Protects the system from destructive actions, preventing accidental data loss or unauthorized modifications.
 * - Acts as an interception layer between the LLM request handler and tool execution engine.
 * - Contains `isDangerousToolCall` to validate specific tool requests and `isDangerousCommand` to scan command strings for blacklisted operations.
 * - Fits into the security pipeline by blocking unsafe commands, ensuring that only validated tool calls proceed to execution.
 */

@Service
public class AgentSafetyService {

    public boolean isDangerousToolCall(LlmToolCallDTO toolCall) {
        if ("execute_command".equals(toolCall.name())) {
            Map<String, Object> args = toolCall.arguments();
            if (args != null && args.containsKey("command")) {
                String cmd = (String) args.get("command");
                return isDangerousCommand(cmd);
            }
        }
        return false;
    }

    private boolean isDangerousCommand(String cmd) {
        if (cmd == null) return false;
        String lower = cmd.toLowerCase();
        return lower.contains("rm ") || lower.contains("del ") || lower.contains("format ")
                || lower.contains("remove-item") || lower.contains("stop-process")
                || lower.contains("restart-computer") || lower.contains("shutdown");
    }
}
