package dev.mark.skill.core;

import dev.mark.skill.dto.SkillExecutionResult;

import java.util.Map;

/**
 * Common contract for Tier-0 deterministic Ultra Instinct skills.
 * Executes pre-verified workflows directly in < 50ms with zero LLM calls.
 */
public interface Skill {

    /** Unique skill identifier (e.g. "whatsapp.send_message"). */
    String name();

    /** Human-readable description of what this skill does. */
    String description();

    /** Returns true if this skill can deterministically handle the user prompt. */
    boolean canHandle(String goal);

    /** Extracts semantic slots from the user prompt. */
    Map<String, Object> extractSlots(String goal);

    /** Executes the deterministic skill without invoking an LLM. */
    SkillExecutionResult execute(String taskId, Map<String, Object> slots);
}
