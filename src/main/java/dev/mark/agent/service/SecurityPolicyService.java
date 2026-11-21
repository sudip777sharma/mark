package dev.mark.agent.service;

import dev.mark.tool.contract.ToolContract;
import dev.mark.tool.contract.ToolRiskClassification;
import dev.mark.tool.dto.HumanInterventionRequest;
import dev.mark.llm.dto.LlmToolCallDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Enterprise security and permission engine for Mark.
 * Replaces naive substring blacklists with permission scopes, risk tiering, and exact-action approval.
 */
@Service
public class SecurityPolicyService {

    private static final Logger log = LoggerFactory.getLogger(SecurityPolicyService.class);

    private static final List<Pattern> CRITICAL_COMMAND_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(format|diskpart|vssadmin|bcdedit)\\b"),
            Pattern.compile("(?i)\\b(rmdir|rd)\\s+.*(/[sS]|-[rR])"),
            Pattern.compile("(?i)\\b(rm|del)\\s+.*(-[rRfF]+|/[sS]|/[fF]|/[qQ])\\s+([/\\\\]|\\.|\\*|[a-zA-Z]:[/\\\\])"),
            Pattern.compile("(?i)\\b(shutdown|restart-computer|stop-computer)\\b"),
            Pattern.compile("(?i)\\b(powershell|pwsh)\\s+.*(-enc|-encodedcommand)\\b"),
            Pattern.compile("(?i)\\b(curl|wget|invoke-webrequest)\\b.*\\|\\s*(sh|bash|powershell|cmd)"),
            Pattern.compile("(?i):\\(\\)\\s*\\{\\s*:\\|:&\\s*\\};:") // fork bomb
    );

    public record SecurityEvaluation(
            boolean allowed,
            boolean requiresApproval,
            String reason,
            HumanInterventionRequest approvalRequest
    ) {
        public static SecurityEvaluation permit() {
            return new SecurityEvaluation(true, false, null, null);
        }

        public static SecurityEvaluation deny(String reason) {
            return new SecurityEvaluation(false, false, reason, null);
        }

        public static SecurityEvaluation requireApproval(String title, String prompt, Map<String, Object> details) {
            HumanInterventionRequest request = HumanInterventionRequest.confirmation(title, prompt, details);
            return new SecurityEvaluation(true, true, prompt, request);
        }
    }

    public SecurityEvaluation evaluate(ToolContract contract, Map<String, Object> arguments) {
        if (contract == null) {
            return SecurityEvaluation.deny("Tool execution denied: no contract defined");
        }

        // 1. Filesystem Deletion Safeguards
        if ("delete_file".equals(contract.toolName())) {
            String path = arguments != null ? String.valueOf(arguments.get("path")) : "";
            String normalizedPath = path.trim().replace('\\', '/');
            if (normalizedPath.equals(".") || normalizedPath.equals("./") || normalizedPath.isBlank() || normalizedPath.equals("/")) {
                log.warn("!!! [SECURITY:BLOCKED] Attempted recursive deletion of workspace root: path='{}'", path);
                return SecurityEvaluation.deny("Attempted recursive deletion of workspace root is blocked by security policy.");
            }

            // Require confirmation for directory deletion
            return SecurityEvaluation.requireApproval(
                    "Delete File Confirmation",
                    "Permanently delete path '" + path + "'?",
                    Map.of("tool", "delete_file", "path", path)
            );
        }

        // 2. Shell Command Safeguards
        if ("execute_command".equals(contract.toolName())) {
            String cmd = arguments != null && arguments.containsKey("command") ? String.valueOf(arguments.get("command")) : "";
            for (Pattern pattern : CRITICAL_COMMAND_PATTERNS) {
                if (pattern.matcher(cmd).find()) {
                    log.warn("!!! [SECURITY:BLOCKED] Destructive command matched critical security pattern: cmd='{}'", cmd);
                    return SecurityEvaluation.deny("Command blocked by security policy: destructive system operation detected.");
                }
            }

            // High impact commands require explicit confirmation
            String lower = cmd.toLowerCase();
            if (lower.contains("drop ") || lower.contains("truncate ") || lower.contains("git reset --hard") || lower.contains("taskkill /f")) {
                return SecurityEvaluation.requireApproval(
                        "Execute High-Impact Command",
                        "Authorize execution of command: " + cmd + "?",
                        Map.of("tool", "execute_command", "command", cmd)
                );
            }
        }

        // 3. High Risk Destructive or External Actions
        if (contract.riskClassification() == ToolRiskClassification.HIGH_RISK_DESTRUCTIVE) {
            return SecurityEvaluation.requireApproval(
                    "Destructive Action Authorization",
                    "Authorize high-risk action with tool " + contract.toolName() + "?",
                    arguments != null ? arguments : Map.of()
            );
        }

        return SecurityEvaluation.permit();
    }
}
