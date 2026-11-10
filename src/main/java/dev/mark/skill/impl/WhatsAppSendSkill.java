package dev.mark.skill.impl;

import dev.mark.agent.model.AgentStepModel;
import dev.mark.skill.core.Skill;
import dev.mark.skill.dto.SkillExecutionResult;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import dev.mark.tool.impl.browser.WhatsAppWebTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tier-0 Deterministic Skill for WhatsApp messaging.
 * Executes in < 50ms with direct DOM dispatch and Level-3 delivery verification.
 */
@Component
public class WhatsAppSendSkill implements Skill {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppSendSkill.class);

    private static final List<Pattern> PATTERNS = List.of(
            // "Send 'hi' to 8280901077 on WhatsApp"
            Pattern.compile("(?i)^send\\s+[\"']?(.*?)[\"']?\\s+to\\s+[\"']?(.*?)[\"']?\\s+on\\s+whatsapp$"),
            // "Send message 'hi' to 8280901077 on WhatsApp"
            Pattern.compile("(?i)^send\\s+message\\s+[\"']?(.*?)[\"']?\\s+to\\s+[\"']?(.*?)[\"']?\\s+on\\s+whatsapp$"),
            // "Text 8280901077 on WhatsApp: 'hi'"
            Pattern.compile("(?i)^text\\s+[\"']?(.*?)[\"']?\\s+on\\s+whatsapp[:\\s]+[\"']?(.*?)[\"']?$"),
            // "WhatsApp 8280901077: 'hi'"
            Pattern.compile("(?i)^whatsapp\\s+[\"']?(.*?)[\"']?[:\\s]+[\"']?(.*?)[\"']?$"),
            // "Send to 8280901077 on WhatsApp: 'hi'"
            Pattern.compile("(?i)^send\\s+to\\s+[\"']?(.*?)[\"']?\\s+on\\s+whatsapp[:\\s]+[\"']?(.*?)[\"']?$")
    );

    private final WhatsAppWebTool whatsAppWebTool;

    public WhatsAppSendSkill(WhatsAppWebTool whatsAppWebTool) {
        this.whatsAppWebTool = whatsAppWebTool;
    }

    @Override
    public String name() {
        return "whatsapp.send_message";
    }

    @Override
    public String description() {
        return "Deterministically sends a WhatsApp message and verifies DOM delivery in < 50ms without LLM overhead.";
    }

    @Override
    public boolean canHandle(String goal) {
        if (goal == null || goal.isBlank()) return false;
        String clean = goal.trim();
        for (Pattern p : PATTERNS) {
            if (p.matcher(clean).matches()) return true;
        }
        return false;
    }

    @Override
    public Map<String, Object> extractSlots(String goal) {
        if (goal == null) return Map.of();
        String clean = goal.trim();

        // Pattern 1 & 2: group 1 = message, group 2 = contact
        Matcher m1 = Pattern.compile("(?i)^send\\s+(?:message\\s+)?[\"']?(.*?)[\"']?\\s+to\\s+[\"']?(.*?)[\"']?\\s+on\\s+whatsapp$").matcher(clean);
        if (m1.matches()) {
            return Map.of("message", cleanSlot(m1.group(1)), "contact", cleanSlot(m1.group(2)));
        }

        // Pattern 3: group 1 = contact, group 2 = message
        Matcher m2 = Pattern.compile("(?i)^text\\s+[\"']?(.*?)[\"']?\\s+on\\s+whatsapp[:\\s]+[\"']?(.*?)[\"']?$").matcher(clean);
        if (m2.matches()) {
            return Map.of("contact", cleanSlot(m2.group(1)), "message", cleanSlot(m2.group(2)));
        }

        // Pattern 4: group 1 = contact, group 2 = message
        Matcher m3 = Pattern.compile("(?i)^whatsapp\\s+[\"']?(.*?)[\"']?[:\\s]+[\"']?(.*?)[\"']?$").matcher(clean);
        if (m3.matches()) {
            return Map.of("contact", cleanSlot(m3.group(1)), "message", cleanSlot(m3.group(2)));
        }

        // Pattern 5: group 1 = contact, group 2 = message
        Matcher m4 = Pattern.compile("(?i)^send\\s+to\\s+[\"']?(.*?)[\"']?\\s+on\\s+whatsapp[:\\s]+[\"']?(.*?)[\"']?$").matcher(clean);
        if (m4.matches()) {
            return Map.of("contact", cleanSlot(m4.group(1)), "message", cleanSlot(m4.group(2)));
        }

        return Map.of();
    }

    private String cleanSlot(String s) {
        if (s == null) return "";
        String trimmed = s.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    @Override
    public SkillExecutionResult execute(String taskId, Map<String, Object> slots) {
        String contact = String.valueOf(slots.getOrDefault("contact", "")).trim();
        String message = String.valueOf(slots.getOrDefault("message", "")).trim();

        if (contact.isBlank() || message.isBlank()) {
            return SkillExecutionResult.failed("Missing required slots: contact='" + contact + "', message='" + message + "'", List.of());
        }

        log.info("=== [L0_SKILL:WHATSAPP_START] taskId={} contact='{}' msgLength={}", taskId, contact, message.length());

        ToolRequestDTO request = new ToolRequestDTO(
                UUID.randomUUID(),
                "whatsapp_web",
                Map.of(
                        "action", "send_message",
                        "contact", contact,
                        "message", message
                )
        );

        ToolResultDTO result = whatsAppWebTool.execute(request);

        List<AgentStepModel> steps = new ArrayList<>();
        steps.add(new AgentStepModel(
                1,
                "L0 Ultra Instinct WhatsApp Execution",
                "whatsapp_web",
                "{\"action\":\"send_message\",\"contact\":\"" + contact + "\",\"message\":\"" + message + "\"}",
                result.successful() ? "SUCCESS: " + result.observation() : "FAILED: " + result.observation(),
                "L0_DETERMINISTIC",
                Instant.now()
        ));

        if (result.interventionRequest() != null) {
            log.info("=== [L0_SKILL:INTERVENTION_REQUIRED] taskId={} type={}", taskId, result.interventionRequest().type());
            return SkillExecutionResult.requiresIntervention(result.interventionRequest(), result.observation(), steps);
        }

        if (result.successful() && Boolean.TRUE.equals(result.metadata().get("verified"))) {
            log.info("=== [L0_SKILL:WHATSAPP_VERIFIED] taskId={} Verified in DOM!", taskId);
            return SkillExecutionResult.completed("Message successfully sent and verified on WhatsApp: " + result.observation(), steps);
        }

        return SkillExecutionResult.failed("Execution failed: " + result.observation(), steps);
    }
}
