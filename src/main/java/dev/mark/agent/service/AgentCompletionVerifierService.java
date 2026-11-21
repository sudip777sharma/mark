package dev.mark.agent.service;

import dev.mark.agent.model.AgentVerificationResultModel;
import dev.mark.agent.model.AgentWorldStateModel;
import dev.mark.llm.dto.LlmMessageDTO;
import dev.mark.llm.dto.LlmToolCallDTO;
import dev.mark.llm.dto.LlmToolObservationDTO;
import dev.mark.skill.dto.SkillExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class AgentCompletionVerifierService {

    private static final Logger log = LoggerFactory.getLogger(AgentCompletionVerifierService.class);

    private static final Pattern ACTION_KEYWORD_PATTERN = Pattern.compile(
            "(?i)\\b(send|text|message|ping|write|create|delete|remove|record|click|press|open|launch|run|download|post|update|modify|edit)\\b"
    );
    private static final Pattern QUERY_OR_GREETING_PATTERN = Pattern.compile(
            "(?i)^\\s*(what|who|where|when|why|how|explain|describe|tell\\s+me|hello|hi|hey|thank(?:s|\\s+you)|bye|goodbye|see\\s+you|good\\s+morning|good\\s+afternoon|good\\s+evening|good\\s+night|how\\s+are\\s+you|can\\s+you\\s+help|yes|no|ok(?:ay)?|sure|alright|great|cool)\\b.*"
    );
    private static final Pattern FILE_MUTATION_GOAL_PATTERN = Pattern.compile(
            "(?i).*\\b(create|write|save|generate|modify|edit|update|delete|remove)\\b.*\\b(file|folder|directory|script|code|txt|json|md|py|java|csv|html)\\b.*"
    );

    public AgentVerificationResultModel verify(
            String goal,
            List<LlmMessageDTO> messages,
            AgentWorldStateModel worldState) {

        String completion = messages.isEmpty() ? "" : messages.get(messages.size() - 1).content();
        if (completion == null || completion.isBlank()) {
            return failure(goal, "Completion is empty");
        }

        String lowerCompletion = completion.toLowerCase(Locale.ROOT).trim();
        if (lowerCompletion.contains("could you please clarify") || lowerCompletion.contains("please clarify")
                || lowerCompletion.contains("i need to clarify") || lowerCompletion.contains("clarification needed")
                || (lowerCompletion.endsWith("?") && !isDirectResponseGoal(goal))) {
            return failure(goal, "Completion is asking for user clarification. Call the 'ask_user' tool to pause and prompt the user instead of finishing.");
        }

        if (isMultiActionGoal(goal)) {
            return failure(goal, "This goal contains multiple actions, but every requested postcondition is not yet independently verifiable.");
        }

        if (isBrowserNavigationGoal(goal)) {
            return hasVerifiedNavigation(goal, messages)
                    ? success(goal, "browser reached the requested destination")
                    : failure(goal, "Browser navigation did not reach a destination that matches the request.");
        }

        if (isApplicationLaunchGoal(goal)) {
            return isRequestedApplicationActive(goal, worldState)
                    ? success(goal, "requested application is active in the observed environment")
                    : failure(goal, "The requested application is not confirmed as active.");
        }

        String whatsappAction = requiredWhatsAppAction(goal);
        if (whatsappAction != null) {
            boolean verified = hasVerifiedWhatsAppEvidence(goal, whatsappAction, messages);
            return verified
                    ? success(goal, "matching WhatsApp action and postcondition evidence")
                    : failure(goal, "Goal requires WhatsApp messaging/verification for action '" + whatsappAction + "' with matching evidence.");
        }
        if (goal != null && goal.toLowerCase(Locale.ROOT).contains("whatsapp")
                && !isInformationalQuestion(goal)) {
            return failure(goal, "This WhatsApp goal has no supported action-specific verifier; completion needs a matching observable postcondition.");
        }

        if (isFileMutationGoal(goal)) {
            boolean verified = hasVerifiedFileMutation(goal, messages);
            return verified
                    ? success(goal, "matching file mutation evidence")
                    : failure(goal, "Goal requires creating or modifying a file with a matching successful mutation and verified target path.");
        }

        if (isTimeSensitiveInformationGoal(goal)) {
            return hasGroundedBrowserEvidence(completion, messages)
                    ? success(goal, "answer is grounded in a successful browser read")
                    : failure(goal, "This time-sensitive answer has no matching successful browser read or extract evidence.");
        }

        if (isDirectResponseGoal(goal)) {
            return success(goal, "direct informational or conversational response");
        }

        if (isEnvironmentQueryGoal(goal) && worldState != null && containsWorldStateValue(completion, goal, worldState)) {
            return success(goal, "completion matches the requested environment observation");
        }

        return failure(goal, "Completion is not supported by available evidence. Non-consequential inspection tools alone do not verify task completion.");
    }

    public AgentVerificationResultModel verifySkill(String goal, SkillExecutionResult result, AgentWorldStateModel worldState) {
        if (result == null || !result.success()) {
            return failure(goal, "Deterministic skill did not report success.");
        }
        String toolName = result.steps().isEmpty() ? "" : result.steps().get(result.steps().size() - 1).toolName();
        LlmToolObservationDTO observation = new LlmToolObservationDTO(
                "skill-result", toolName, true, result.summary(), result.evidence());
        return verify(goal, List.of(
                LlmMessageDTO.toolObservation(observation),
                LlmMessageDTO.assistantCompletion(result.summary())), worldState);
    }

    private String requiredWhatsAppAction(String goal) {
        if (goal == null) return null;
        String lower = goal.toLowerCase(Locale.ROOT);
        boolean whatsappTarget = lower.contains("whatsapp") || lower.matches(".*\\b(send|text)\\b.*\\b(message|msg|ping)\\b.*");
        if (!whatsappTarget) return null;

        if (isInformationalQuestion(goal) && !isTimeSensitiveInformationGoal(goal)) return null;
        if (lower.contains("status") && Pattern.compile("(?i)\\b(post|put|set|update|publish)\\b").matcher(goal).find()) return "post_status";
        if (lower.contains("video call")) return "start_video_call";
        if (lower.contains("call")) return "start_audio_call";
        if (Pattern.compile("(?i)\\b(verify|confirm|check)\\b.*\\b(message|sent)\\b|\\b(whether|if)\\b.*\\bmessage\\b.*\\bsent\\b").matcher(goal).find()) return "verify_message";
        if (lower.contains("unread") && lower.contains("status")) return "list_unseen_statuses";
        if (lower.contains("unread")) return "list_unread_chats";
        if (lower.contains("my status") && Pattern.compile("(?i)\\b(check|view|what|whether|active)\\b").matcher(goal).find()) return "get_my_status";
        if (lower.contains("status") && Pattern.compile("(?i)\\b(check|view|read|what|whether|see)\\b").matcher(goal).find()) return "view_contact_status";
        if (Pattern.compile("(?i)\\b(read|show|summarize|list|get)\\b.*\\b(messages?|chats?|conversations?)\\b").matcher(goal).find()) return "read_chat";
        if (Pattern.compile("(?i)\\b(open|start|check)\\b.*\\b(session|login|logged in|whatsapp web)\\b").matcher(goal).find()) return "open_session";
        if (Pattern.compile("(?i)\\b(open|go to)\\b.*\\b(chat|conversation)\\b").matcher(goal).find()) return "open_chat";
        if (Pattern.compile("(?i)\\b(send|text|ping|say)\\b|^\\s*message\\b|\\bmessage\\s+.+\\s+to\\s+").matcher(goal).find()) return "send_message";
        return null;
    }

    private boolean hasVerifiedWhatsAppEvidence(String goal, String expectedAction, List<LlmMessageDTO> messages) {
        return messages.stream()
                .filter(message -> "tool".equals(message.role()) && message.toolObservation() != null)
                .map(message -> message.toolObservation())
                .filter(LlmToolObservationDTO::successful)
                .filter(observation -> expectedObservationAction(expectedAction).equals(stringValue(observation.metadata().get("action"))))
                .anyMatch(observation -> matchesWhatsAppRequest(goal, expectedAction, observation, messages));
    }

    private boolean matchesWhatsAppRequest(String goal, String expectedAction, LlmToolObservationDTO observation,
            List<LlmMessageDTO> messages) {
        Map<String, Object> metadata = observation.metadata();
        if (expectedAction.equals("get_my_status")) {
            if (!(metadata.get("has_active_status") instanceof Boolean active)) return false;
            String completion = messages.isEmpty() ? "" : messages.get(messages.size() - 1).content();
            String lowerCompletion = completion == null ? "" : completion.toLowerCase(Locale.ROOT);
            return active
                    ? lowerCompletion.contains("active") && !lowerCompletion.contains("no active")
                    : lowerCompletion.contains("no active") || lowerCompletion.contains("not active") || lowerCompletion.contains("no status");
        }
        if (expectedAction.equals("open_session")) {
            return "LOGGED_IN".equalsIgnoreCase(stringValue(metadata.get("status")));
        }
        if (expectedAction.equals("open_chat") || expectedAction.equals("view_contact_status")) {
            String requiredStatus = expectedAction.equals("open_chat") ? "OPENED" : "VIEWED";
            String contact = stringValue(metadata.get("contact"));
            return requiredStatus.equalsIgnoreCase(stringValue(metadata.get("status")))
                    && contact != null && containsNormalized(goal, contact);
        }
        if (expectedAction.equals("read_chat")) {
            if (!(metadata.get("count") instanceof Number count)) return false;
            if (count.intValue() == 0) {
                return completionText(messages).toLowerCase(Locale.ROOT).contains("0 recent message")
                        || completionText(messages).toLowerCase(Locale.ROOT).contains("no messages");
            }
            String contact = stringValue(metadata.get("contact"));
            return (contact == null || "active_chat".equalsIgnoreCase(contact) || containsNormalized(goal, contact))
                    && hasTextOverlap(completionText(messages), observation.observation());
        }
        if (expectedAction.equals("list_unread_chats") || expectedAction.equals("list_unseen_statuses")) {
            String countKey = expectedAction.equals("list_unread_chats") ? "unread_chats_count" : "unseen_count";
            if (!(metadata.get(countKey) instanceof Number count)) return false;
            String completion = completionText(messages).toLowerCase(Locale.ROOT);
            if (count.intValue() == 0) return completion.contains("no unread") || completion.contains("no unseen") || completion.contains("0 status");
            return hasTextOverlap(completion, observation.observation());
        }
        if (expectedAction.equals("verify_message")) {
            if (!isVerified(metadata)) return false;
            String expectedMessage = firstString(metadata.get("expected_message"), argumentForObservation(messages, observation, "expected_message"));
            String contact = firstString(metadata.get("contact"), argumentForObservation(messages, observation, "contact"));
            return expectedMessage != null && containsNormalized(goal, expectedMessage)
                    && containsNormalized(observation.observation(), expectedMessage)
                    && (contact == null || containsNormalized(goal, contact));
        }
        if (!isVerified(metadata)) return false;

        Optional<LlmToolCallDTO> call = messages.stream()
                .flatMap(message -> message.toolCalls().stream())
                .filter(toolCall -> observation.toolCallId().equals(toolCall.id()))
                .findFirst();

        String contact = firstString(metadata.get("contact"), argument(call, "contact"));
        String messageText = firstString(metadata.get("message"), metadata.get("expected_message"),
                metadata.get("caption"), metadata.get("caption_prefix"), argument(call, "message"));

        if (expectedAction.equals("send_message")) {
            List<String> quoted = quotedValues(observation.observation()).stream()
                    .filter(value -> containsNormalized(goal, value))
                    .toList();
            if (messageText == null && !quoted.isEmpty()) messageText = quoted.get(quoted.size() - 1);
            if (contact == null && quoted.size() > 1) contact = quoted.get(0);
            if (messageText == null || !containsNormalized(goal, messageText)
                    || !containsNormalized(observation.observation(), messageText)) return false;
            if (contact != null) {
                return containsNormalized(goal, contact) || containsNormalized(observation.observation(), contact);
            }
            String goalDigits = digits(goal);
            return goalDigits.isEmpty() || digits(observation.observation()).contains(goalDigits);
        }

        if (expectedAction.equals("post_status")) {
            if (messageText != null) {
                return containsNormalized(goal, messageText)
                        && containsNormalized(observation.observation(), messageText);
            }
            String filePath = firstString(metadata.get("file_path"), metadata.get("directory"), argument(call, "file_path"));
            return filePath != null && goalMentionsPath(goal, filePath);
        }

        if (expectedAction.equals("start_audio_call") || expectedAction.equals("start_video_call")) {
            return contact != null && containsNormalized(goal, contact)
                    && Boolean.TRUE.equals(metadata.get("verified"));
        }

        return false;
    }

    private boolean hasVerifiedFileMutation(String goal, List<LlmMessageDTO> messages) {
        String lower = goal == null ? "" : goal.toLowerCase(Locale.ROOT);
        boolean deletion = Pattern.compile("(?i)\\b(delete|remove)\\b").matcher(lower).find();
        boolean creation = Pattern.compile("(?i)\\b(create|write|save|generate)\\b").matcher(lower).find();

        return messages.stream()
                .filter(message -> "tool".equals(message.role()) && message.toolObservation() != null)
                .map(message -> message.toolObservation())
                .filter(LlmToolObservationDTO::successful)
                .filter(observation -> {
                    String tool = observation.toolName();
                    if (deletion) return "delete_file".equals(tool);
                    if (creation) return "write_file".equals(tool);
                    return "replace_in_file".equals(tool) || "write_file".equals(tool);
                })
                .anyMatch(observation -> {
                    Object rawPath = observation.metadata().get("path");
                    if (!(rawPath instanceof String path) || path.isBlank()) return false;
                    if (!goalMentionsPath(goal, path)) return false;
                    if (observation.metadata().containsKey("verified")) {
                        return Boolean.TRUE.equals(observation.metadata().get("verified"));
                    }
                    return observation.observation().toLowerCase(Locale.ROOT).contains("success")
                            || observation.observation().toLowerCase(Locale.ROOT).startsWith("written ");
                });
    }

    private boolean isBrowserNavigationGoal(String goal) {
        if (goal == null) return false;
        String lower = goal.toLowerCase(Locale.ROOT);
        boolean navigationVerb = Pattern.compile("(?i)\\b(open|navigate|visit|browse)\\b|go\\s+to").matcher(goal).find();
        boolean browserTarget = lower.contains("browser") || lower.contains("web") || lower.contains("website")
                || lower.contains("webpage") || Pattern.compile("(?i)\\b[a-z0-9-]+\\.(com|org|net|io|gov|edu)\\b").matcher(goal).find();
        boolean alsoRequestsAction = Pattern.compile("(?i)\\b(send|text|message|ping|post|call|delete|remove|create|write|count|search|find|download|summarize|extract|check|verify)\\b").matcher(goal).find();
        return navigationVerb && browserTarget && !alsoRequestsAction && !lower.contains(" and ");
    }

    private boolean hasVerifiedNavigation(String goal, List<LlmMessageDTO> messages) {
        return messages.stream()
                .filter(message -> "tool".equals(message.role()) && message.toolObservation() != null)
                .map(message -> message.toolObservation())
                .filter(LlmToolObservationDTO::successful)
                .filter(observation -> "browser_navigate".equals(observation.toolName()))
                .filter(observation -> isVerified(observation.metadata()))
                .anyMatch(observation -> {
                    String actualUrl = stringValue(observation.metadata().get("actual_url"));
                    if (actualUrl == null) return false;
                    try {
                        String host = java.net.URI.create(actualUrl).getHost();
                        if (host == null) return false;
                        String[] labels = host.toLowerCase(Locale.ROOT).split("\\.");
                        for (String label : labels) {
                            if (!label.equals("www") && label.length() > 1 && containsNormalized(goal, label)) return true;
                        }
                    } catch (RuntimeException ignored) {
                    }
                    return false;
                });
    }

    private boolean isApplicationLaunchGoal(String goal) {
        if (goal == null || QUERY_OR_GREETING_PATTERN.matcher(goal).matches()) return false;
        if (!Pattern.compile("(?i)\\b(open|launch|start)\\b").matcher(goal).find()) return false;
        String lower = goal.toLowerCase(Locale.ROOT);
        boolean alsoRequestsAction = Pattern.compile("(?i)\\b(send|text|message|ping|post|call|delete|remove|create|write|edit|update|count|search|find|download|check|verify)\\b").matcher(goal).find();
        return !isBrowserNavigationGoal(goal)
                && !alsoRequestsAction && !lower.contains("file") && !lower.contains("folder") && !lower.contains("directory")
                && !lower.contains(" and ");
    }

    private boolean isRequestedApplicationActive(String goal, AgentWorldStateModel worldState) {
        if (worldState == null) return false;
        return containsNormalized(goal, worldState.activeApplication())
                || containsNormalized(goal, worldState.activeWindow());
    }

    private boolean isVerified(Map<String, Object> metadata) {
        if (metadata.containsKey("verified")) {
            return Boolean.TRUE.equals(metadata.get("verified"))
                    || "true".equalsIgnoreCase(String.valueOf(metadata.get("verified")));
        }
        return "VERIFIED".equalsIgnoreCase(String.valueOf(metadata.get("verification_status")));
    }

    private String argument(Optional<LlmToolCallDTO> call, String key) {
        if (call.isEmpty()) return null;
        return stringValue(call.get().arguments().get(key));
    }

    private String argumentForObservation(List<LlmMessageDTO> messages, LlmToolObservationDTO observation, String key) {
        Optional<LlmToolCallDTO> call = messages.stream()
                .flatMap(message -> message.toolCalls().stream())
                .filter(toolCall -> observation.toolCallId().equals(toolCall.id()))
                .findFirst();
        return argument(call, key);
    }

    private String expectedObservationAction(String expectedAction) {
        return "verify_message".equals(expectedAction) ? "read_chat" : expectedAction;
    }

    private String completionText(List<LlmMessageDTO> messages) {
        if (messages.isEmpty()) return "";
        String completion = messages.get(messages.size() - 1).content();
        return completion == null ? "" : completion;
    }

    private boolean hasTextOverlap(String first, String second) {
        Set<String> firstTokens = meaningfulTokens(first);
        Set<String> secondTokens = meaningfulTokens(second);
        if (firstTokens.isEmpty()) return false;
        long overlap = firstTokens.stream().filter(secondTokens::contains).count();
        return overlap >= Math.min(2, firstTokens.size());
    }

    private String firstString(Object... values) {
        for (Object value : values) {
            String text = stringValue(value);
            if (text != null && !text.isBlank()) return text;
        }
        return null;
    }

    private String stringValue(Object value) {
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    private boolean goalMentionsPath(String goal, String path) {
        String normalizedPath = path.replace('\\', '/');
        int separator = normalizedPath.lastIndexOf('/');
        String name = separator >= 0 ? normalizedPath.substring(separator + 1) : normalizedPath;
        return containsNormalized(goal, normalizedPath) || (!name.isBlank() && containsNormalized(goal, name));
    }

    private boolean containsNormalized(String text, String expected) {
        if (text == null || expected == null || expected.isBlank()) return false;
        String normalizedText = normalize(text);
        String normalizedExpected = normalize(expected);
        if (normalizedExpected.isBlank()) return false;
        if (normalizedText.contains(normalizedExpected)) return true;
        String expectedDigits = digits(expected);
        return expectedDigits.length() >= 7 && digits(text).contains(expectedDigits);
    }

    private String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
    }

    private String digits(String text) {
        return text == null ? "" : text.replaceAll("\\D", "");
    }

    private List<String> quotedValues(String text) {
        if (text == null) return List.of();
        java.util.regex.Matcher matcher = Pattern.compile("['\"]([^'\"]+)['\"]").matcher(text);
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group(1).trim());
        }
        return values;
    }

    private boolean isFileMutationGoal(String goal) {
        return goal != null && FILE_MUTATION_GOAL_PATTERN.matcher(goal).matches();
    }

    private boolean isTimeSensitiveInformationGoal(String goal) {
        return goal != null && Pattern.compile("(?i)\\b(today|latest|current|currently|now|recent|news|weather|price|rate|schedule|score|stock|exchange rate)\\b")
                .matcher(goal).find();
    }

    private boolean isMultiActionGoal(String goal) {
        if (goal == null || isInformationalQuestion(goal)
                || !Pattern.compile("(?i)\\b(and|then|afterwards)\\b|after that").matcher(goal).find()) return false;
        return Pattern.compile("(?i)\\b(send|text|ping|post|publish|call|create|write|save|delete|remove|modify|edit|update|open|launch|navigate|visit|search|find|count|verify|check|read|download)\\b")
                .matcher(goal).results().count() > 1;
    }

    private boolean hasGroundedBrowserEvidence(String completion, List<LlmMessageDTO> messages) {
        Set<String> answerTokens = meaningfulTokens(completion);
        if (answerTokens.isEmpty()) return false;
        return messages.stream()
                .filter(message -> "tool".equals(message.role()) && message.toolObservation() != null)
                .map(message -> message.toolObservation())
                .filter(LlmToolObservationDTO::successful)
                .filter(observation -> "browser_read_page".equals(observation.toolName())
                        || "browser_extract".equals(observation.toolName()))
                .anyMatch(observation -> {
                    Set<String> sourceTokens = meaningfulTokens(observation.observation());
                    long overlap = answerTokens.stream().filter(sourceTokens::contains).count();
                    return overlap >= Math.min(2, answerTokens.size());
                });
    }

    private Set<String> meaningfulTokens(String text) {
        if (text == null) return Set.of();
        return Pattern.compile("[\\p{L}\\p{N}]{3,}").matcher(text.toLowerCase(Locale.ROOT))
                .results()
                .map(java.util.regex.MatchResult::group)
                .filter(token -> !Set.of("the", "and", "for", "with", "from", "this", "that", "was", "are", "were", "you", "your", "have", "has", "had", "not", "but", "can", "will", "about", "today", "latest", "current").contains(token))
                .collect(java.util.stream.Collectors.toSet());
    }

    private boolean isDirectResponseGoal(String goal) {
        if (goal == null || goal.isBlank()) return false;
        String normalized = goal.toLowerCase(Locale.ROOT).trim();
        if (ACTION_KEYWORD_PATTERN.matcher(normalized).find() && !isInformationalQuestion(goal)) return false;
        return QUERY_OR_GREETING_PATTERN.matcher(normalized).matches();
    }

    private boolean isInformationalQuestion(String goal) {
        if (goal == null || !Pattern.compile("(?i)^\\s*(what is|who is|what are|who are|how does|how do|how to|how can|how would|how should|why does|why is|explain|describe|tell me about)\\b")
                .matcher(goal).find()) return false;
        return !Pattern.compile("(?i)\\b(my|our|unread|latest|current|currently|recent|today|now|last)\\b")
                .matcher(goal).find();
    }

    private boolean isEnvironmentQueryGoal(String goal) {
        if (goal == null) return false;
        String lower = goal.toLowerCase(Locale.ROOT);
        return lower.contains("window") || lower.contains("screen") || lower.contains("application")
                || lower.contains("title") || lower.contains("running") || lower.contains("desktop");
    }

    private boolean containsWorldStateValue(String completion, String goal, AgentWorldStateModel worldState) {
        String lowerGoal = goal.toLowerCase(Locale.ROOT);
        String relevantValue = lowerGoal.contains("url") || lowerGoal.contains("website") || lowerGoal.contains("browser")
                ? worldState.browserUrl()
                : lowerGoal.contains("window") || lowerGoal.contains("title")
                        ? worldState.activeWindow()
                        : worldState.activeApplication();
        return relevantValue != null && containsNormalized(completion, relevantValue);
    }

    private AgentVerificationResultModel success(String goal, String reason) {
        log.info("<<< [VERIFIER:SUCCESS] goal='{}' evidence='{}'", goal, reason);
        return AgentVerificationResultModel.success();
    }

    private AgentVerificationResultModel failure(String goal, String reason) {
        log.warn("<<< [VERIFIER:FAILED] goal='{}' reason='{}'", goal, reason);
        return AgentVerificationResultModel.failure(reason);
    }
}
