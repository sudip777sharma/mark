package dev.mark.agent.service;

import dev.mark.agent.model.AgentVerificationResultModel;
import dev.mark.agent.model.AgentWorldStateModel;
import dev.mark.llm.dto.LlmMessageDTO;
import dev.mark.llm.dto.LlmToolObservationDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Strict Level-3 Goal Verifier.
 *
 * <p>Validates whether an agent execution has truly satisfied its assigned goal
 * based on typed, domain-specific post-condition evidence rather than self-attested
 * completions or arbitrary non-consequential tool runs.</p>
 */
@Component
public class AgentCompletionVerifierService {

    private static final Logger log = LoggerFactory.getLogger(AgentCompletionVerifierService.class);

    private static final Pattern ACTION_KEYWORD_PATTERN = Pattern.compile(
            "(?i)\\b(send|text|message|ping|write|create|delete|remove|record|click|press|open|launch|run|download|post)\\b"
    );

    private static final Pattern QUERY_OR_GREETING_PATTERN = Pattern.compile(
            "(?i)^\\s*(what|who|where|when|why|how|explain|describe|tell\\s+me|hello|hi|hey|good\\s+morning|good\\s+evening)\\b.*"
    );

    private static final Pattern WHATSAPP_GOAL_PATTERN = Pattern.compile(
            "(?i).*(whatsapp|\\b(send|text)\\b.*\\b(message|msg|ping)\\b).*"
    );

    private static final Pattern FILE_MUTATION_GOAL_PATTERN = Pattern.compile(
            "(?i).*\\b(create|write|save|generate|modify|edit|update|delete|remove)\\b.*\\b(file|folder|directory|script|code|txt|json|md|py|java|csv|html)\\b.*"
    );

    public AgentVerificationResultModel verify(
            String goal,
            List<LlmMessageDTO> messages,
            AgentWorldStateModel worldState) {

        String completionContent = messages.isEmpty() ? "" : messages.get(messages.size() - 1).content();
        if (completionContent == null || completionContent.isBlank()) {
            log.warn("<<< [VERIFIER:FAILED] goal='{}' reason='Completion is empty'", goal);
            return AgentVerificationResultModel.failure("Completion is empty");
        }

        // Reject completion if the agent is asking clarification questions instead of completing the goal
        String lowerCompletion = completionContent.toLowerCase().trim();
        if (lowerCompletion.contains("could you please clarify") || lowerCompletion.contains("please clarify")
                || lowerCompletion.contains("i need to clarify") || lowerCompletion.contains("clarification needed")
                || (lowerCompletion.endsWith("?") && !isDirectResponseGoal(goal))) {
            log.warn("<<< [VERIFIER:FAILED] goal='{}' reason='Completion is asking for user clarification rather than completing the goal'", goal);
            return AgentVerificationResultModel.failure("Completion is asking for user clarification. Call the 'ask_user' tool to pause and prompt the user instead of finishing.");
        }

        // 1. WhatsApp Goal Verification: Requires verified delivery in tool observations
        if (isWhatsAppGoal(goal)) {
            boolean whatsappVerified = hasVerifiedWhatsAppObservation(messages);
            if (whatsappVerified) {
                log.info("<<< [VERIFIER:SUCCESS] goal='{}' validated by verified WhatsApp delivery", goal);
                return AgentVerificationResultModel.success();
            } else {
                log.warn("<<< [VERIFIER:FAILED] goal='{}' reason='Goal requires WhatsApp messaging but no verified delivery was found in execution history'", goal);
                return AgentVerificationResultModel.failure("Goal requires WhatsApp messaging/verification, but no verified delivery was confirmed in tool observations.");
            }
        }

        // 2. File Mutation Goal Verification: Requires successful file/command mutation tool
        if (isFileMutationGoal(goal)) {
            boolean fileMutated = hasSuccessfulFileMutation(messages);
            if (fileMutated) {
                log.info("<<< [VERIFIER:SUCCESS] goal='{}' validated by successful file mutation tool", goal);
                return AgentVerificationResultModel.success();
            } else {
                log.warn("<<< [VERIFIER:FAILED] goal='{}' reason='Goal requires file creation or modification, but no successful file tool execution was recorded'", goal);
                return AgentVerificationResultModel.failure("Goal requires creating or modifying a file, but no successful file tool execution was recorded.");
            }
        }

        // 3. Direct Informational / Conversational Response (Only when no external action was requested)
        if (isDirectResponseGoal(goal)) {
            log.info("<<< [VERIFIER:SUCCESS] goal='{}' validated as direct informational or conversational response", goal);
            return AgentVerificationResultModel.success();
        }

        // 4. World State Query Verification: Valid only if goal asks about the environment/screen/window state
        if (isEnvironmentQueryGoal(goal) && worldState != null && containsWorldStateValue(completionContent, worldState)) {
            log.info("<<< [VERIFIER:SUCCESS] goal='{}' validated by world state observation match", goal);
            return AgentVerificationResultModel.success();
        }

        // 5. Consequential Tool Verification: Inspection tools alone (inspect_ui, list_windows, ask_user, open_session) cannot prove task completion
        boolean hasConsequentialToolSuccess = messages.stream()
                .filter(m -> "tool".equals(m.role()) && m.toolObservation() != null)
                .map(LlmMessageDTO::toolObservation)
                .filter(LlmToolObservationDTO::successful)
                .anyMatch(obs -> {
                    String toolName = obs.toolName() != null ? obs.toolName() : "";
                    return !toolName.equals("inspect_ui")
                            && !toolName.equals("list_windows")
                            && !toolName.equals("ask_user")
                            && !toolName.equals("open_session");
                });

        if (hasConsequentialToolSuccess) {
            log.info("<<< [VERIFIER:SUCCESS] goal='{}' validated by consequential tool observation", goal);
            return AgentVerificationResultModel.success();
        }

        log.warn("<<< [VERIFIER:FAILED] goal='{}' reason='Completion is not supported by available evidence'", goal);
        return AgentVerificationResultModel.failure("Completion is not supported by available evidence. Non-consequential inspection tools alone do not verify task completion.");
    }

    private boolean isWhatsAppGoal(String goal) {
        if (goal == null) return false;
        return WHATSAPP_GOAL_PATTERN.matcher(goal).matches();
    }

    private boolean isFileMutationGoal(String goal) {
        if (goal == null) return false;
        return FILE_MUTATION_GOAL_PATTERN.matcher(goal).matches();
    }

    private boolean isEnvironmentQueryGoal(String goal) {
        if (goal == null) return false;
        String lower = goal.toLowerCase();
        return lower.contains("window") || lower.contains("screen") || lower.contains("application")
                || lower.contains("title") || lower.contains("running") || lower.contains("desktop");
    }

    private boolean hasVerifiedWhatsAppObservation(List<LlmMessageDTO> messages) {
        return messages.stream()
                .filter(m -> "tool".equals(m.role()) && m.toolObservation() != null)
                .map(LlmMessageDTO::toolObservation)
                .filter(obs -> obs.toolName() != null && obs.toolName().contains("whatsapp"))
                .anyMatch(obs -> {
                    if (!obs.successful()) return false;
                    Map<String, Object> meta = obs.metadata();
                    if (meta != null) {
                        Object verifiedObj = meta.get("verified");
                        if (Boolean.TRUE.equals(verifiedObj) || "true".equalsIgnoreCase(String.valueOf(verifiedObj))) {
                            return true;
                        }
                        String vStatus = String.valueOf(meta.get("verification_status"));
                        if ("VERIFIED".equalsIgnoreCase(vStatus)) {
                            return true;
                        }
                    }
                    String text = obs.observation() != null ? obs.observation() : "";
                    return (text.contains("VERIFICATION PASSED") || text.contains("Successfully sent WhatsApp message"))
                            && !text.contains("UNVERIFIED");
                });
    }

    private boolean hasSuccessfulFileMutation(List<LlmMessageDTO> messages) {
        return messages.stream()
                .filter(m -> "tool".equals(m.role()) && m.toolObservation() != null)
                .map(LlmMessageDTO::toolObservation)
                .filter(LlmToolObservationDTO::successful)
                .anyMatch(obs -> {
                    String tool = obs.toolName() != null ? obs.toolName() : "";
                    return tool.contains("file") || tool.contains("command") || tool.contains("directory");
                });
    }

    private boolean isDirectResponseGoal(String goal) {
        if (goal == null || goal.isBlank()) {
            return false;
        }

        String normalized = goal.toLowerCase().trim();

        // If goal mentions external action keywords (e.g. 'whatsapp', 'send', 'create', 'write', etc.),
        // it cannot be satisfied by direct conversational text without tool evidence.
        if (ACTION_KEYWORD_PATTERN.matcher(normalized).find() || normalized.contains("whatsapp")) {
            return false;
        }

        return QUERY_OR_GREETING_PATTERN.matcher(normalized).matches();
    }

    private boolean containsWorldStateValue(String completionContent, AgentWorldStateModel worldState) {
        String text = completionContent.toLowerCase();

        return contains(text, worldState.activeApplication())
                || contains(text, worldState.activeWindow())
                || contains(text, worldState.browserUrl())
                || contains(text, worldState.browserTitle());
    }

    private boolean contains(String text, String value) {
        return value != null
                && !value.isBlank()
                && text.contains(value.toLowerCase());
    }
}
