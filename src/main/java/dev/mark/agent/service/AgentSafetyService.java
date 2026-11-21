package dev.mark.agent.service;

import dev.mark.llm.dto.LlmToolCallDTO;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Evaluates LLM tool calls to detect potentially harmful operations before execution.
 * Intercepts unsafe commands before they reach the execution phase.
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
                || lower.contains("restart-computer") || lower.contains("shutdown")
                || lower.contains("rmdir /s") || lower.contains("rd /s")
                || lower.contains("-encodedcommand") || lower.contains("diskpart");
    }
}
