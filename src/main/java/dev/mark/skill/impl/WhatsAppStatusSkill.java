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
 * Tier-0 Deterministic Skill for WhatsApp Status updates.
 * Executes in < 50ms without LLM overhead.
 */
@Component
public class WhatsAppStatusSkill implements Skill {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppStatusSkill.class);

    private static final List<Pattern> PATTERNS = List.of(
            // "put a whatsapp status happy diwali from mark with amazing caption"
            Pattern.compile("(?i)^put\\s+(?:a\\s+)?whatsapp\\s+status\\s+(.*)$"),
            // "post a whatsapp status ..."
            Pattern.compile("(?i)^post\\s+(?:a\\s+)?whatsapp\\s+status\\s+(.*)$"),
            // "set a whatsapp status ..."
            Pattern.compile("(?i)^set\\s+(?:a\\s+)?whatsapp\\s+status\\s+(.*)$"),
            // "update whatsapp status ..."
            Pattern.compile("(?i)^update\\s+(?:my\\s+)?whatsapp\\s+status\\s+(.*)$"),
            // "post status on whatsapp ..."
            Pattern.compile("(?i)^post\\s+(?:a\\s+)?status\\s+on\\s+whatsapp\\s+(.*)$"),
            // "put status on whatsapp ..."
            Pattern.compile("(?i)^put\\s+(?:a\\s+)?status\\s+on\\s+whatsapp\\s+(.*)$"),
            // "whatsapp status: ..."
            Pattern.compile("(?i)^whatsapp\\s+status[:\\s]+(.*)$")
    );

    private final WhatsAppWebTool whatsAppWebTool;

    public WhatsAppStatusSkill(WhatsAppWebTool whatsAppWebTool) {
        this.whatsAppWebTool = whatsAppWebTool;
    }

    @Override
    public String name() {
        return "whatsapp.post_status";
    }

    @Override
    public String description() {
        return "Deterministically posts a WhatsApp status update in < 50ms without LLM planning overhead.";
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

        for (Pattern p : PATTERNS) {
            Matcher m = p.matcher(clean);
            if (m.matches()) {
                String payload = m.group(1).trim();

                // Check for file / media indicator e.g. with image 'xyz.png'
                Matcher mediaMatcher = Pattern.compile("(?i)(?:with\\s+(?:image|photo|video|file)\\s+[\"']?(.*?)[\"']?\\s+(?:and\\s+caption|with\\s+caption)?\\s*[\"']?(.*?)[\"']?$)|(?:file[:\\s]+[\"']?(.*?)[\"']?)").matcher(payload);
                if (mediaMatcher.find()) {
                    String file = mediaMatcher.group(1) != null ? mediaMatcher.group(1) : mediaMatcher.group(3);
                    String caption = mediaMatcher.group(2) != null ? mediaMatcher.group(2) : "";
                    Map<String, Object> slots = new HashMap<>();
                    if (file != null && !file.isBlank()) slots.put("file_path", cleanSlot(file));
                    if (!caption.isBlank()) slots.put("message", cleanSlot(caption));
                    return slots;
                }

                // Check for quotes
                return Map.of("message", cleanSlot(payload));
            }
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
        String message = String.valueOf(slots.getOrDefault("message", "")).trim();
        String filePath = String.valueOf(slots.getOrDefault("file_path", "")).trim();

        if (message.isBlank() && filePath.isBlank()) {
            return SkillExecutionResult.failed("Missing required status text or media file for WhatsApp status.", List.of());
        }

        log.info("=== [L0_SKILL:WHATSAPP_STATUS_START] taskId={} msgLength={} hasFile={}", taskId, message.length(), !filePath.isBlank());

        Map<String, Object> args = new HashMap<>();
        args.put("action", "post_status");
        if (!message.isBlank()) args.put("message", message);
        if (!filePath.isBlank()) args.put("file_path", filePath);

        ToolRequestDTO request = new ToolRequestDTO(
                UUID.randomUUID(),
                "whatsapp_web",
                args
        );

        ToolResultDTO result = whatsAppWebTool.execute(request);

        List<AgentStepModel> steps = new ArrayList<>();
        steps.add(new AgentStepModel(
                1,
                "L0 Ultra Instinct WhatsApp Status Execution",
                "whatsapp_web",
                "{\"action\":\"post_status\",\"message\":\"" + message + "\"}",
                result.successful() ? "SUCCESS: " + result.observation() : "FAILED: " + result.observation(),
                "L0_DETERMINISTIC",
                Instant.now()
        ));

        if (result.interventionRequest() != null) {
            log.info("=== [L0_SKILL:INTERVENTION_REQUIRED] taskId={} type={}", taskId, result.interventionRequest().type());
            return SkillExecutionResult.requiresIntervention(result.interventionRequest(), result.observation(), steps);
        }

        if (result.successful()) {
            log.info("=== [L0_SKILL:WHATSAPP_STATUS_POSTED] taskId={} Status posted successfully!", taskId);
            return SkillExecutionResult.completed("Status successfully posted to WhatsApp: " + result.observation(), steps);
        }

        return SkillExecutionResult.failed("WhatsApp status execution failed: " + result.observation(), steps);
    }
}
