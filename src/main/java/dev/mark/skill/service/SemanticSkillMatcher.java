package dev.mark.skill.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Normalizes conversational prompts and matches them against skill signatures
 * using fast, local, embedded token rules (< 5ms) without external API or model calls.
 */
@Service
public class SemanticSkillMatcher {

    private static final Pattern PREAMBLE_PATTERN = Pattern.compile(
            "(?i)^(?:hey mark,?|mark,?|please|can you(?: please)?|could you(?: please)?|i want you to|kindly)\\s+"
    );

    private static final Pattern POSTAMBLE_PATTERN = Pattern.compile(
            "(?i)\\s+(?:please|asap|now|thank you|thanks)\\.?$"
    );

    /**
     * Cleans conversational noise from user commands to maximize deterministic match rates.
     */
    public String normalizeGoal(String goal) {
        if (goal == null) return "";
        String trimmed = goal.trim();
        trimmed = PREAMBLE_PATTERN.matcher(trimmed).replaceFirst("");
        trimmed = POSTAMBLE_PATTERN.matcher(trimmed).replaceFirst("");
        return trimmed.trim();
    }
}
