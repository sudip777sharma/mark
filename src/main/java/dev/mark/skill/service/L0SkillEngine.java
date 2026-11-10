package dev.mark.skill.service;

import dev.mark.skill.core.Skill;
import dev.mark.skill.dto.SkillExecutionResult;
import dev.mark.trajectory.service.TrajectoryRecorderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ultra Instinct Tier-0 Execution Engine.
 * Intercepts routine, personalized tasks before the LLM planning loop,
 * routing them to compiled deterministic skills for instant sub-50ms execution.
 */
@Service
public class L0SkillEngine {
    private static final Logger log = LoggerFactory.getLogger(L0SkillEngine.class);

    private final List<Skill> skills;
    private final TrajectoryRecorderService trajectoryRecorderService;
    private final SemanticSkillMatcher semanticMatcher;

    public L0SkillEngine(
            List<Skill> skills,
            TrajectoryRecorderService trajectoryRecorderService,
            SemanticSkillMatcher semanticMatcher) {
        this.skills = skills;
        this.trajectoryRecorderService = trajectoryRecorderService;
        this.semanticMatcher = semanticMatcher;
    }

    public Optional<SkillExecutionResult> tryExecute(String taskId, String goal) {
        if (goal == null || goal.isBlank()) {
            return Optional.empty();
        }

        String normalizedGoal = semanticMatcher.normalizeGoal(goal);

        for (Skill skill : skills) {
            String candidateGoal = skill.canHandle(goal) ? goal
                    : (skill.canHandle(normalizedGoal) ? normalizedGoal : null);

            if (candidateGoal != null) {
                long startTime = System.currentTimeMillis();
                Map<String, Object> slots = skill.extractSlots(candidateGoal);
                log.info("=== [L0_ENGINE:FAST_PATH_MATCHED] taskId={} skill={} candidate='{}' slots={}",
                        taskId, skill.name(), candidateGoal, slots);

                SkillExecutionResult result = skill.execute(taskId, slots);
                long duration = System.currentTimeMillis() - startTime;
                log.info("=== [L0_ENGINE:FAST_PATH_DONE] taskId={} skill={} success={} duration={}ms",
                        taskId, skill.name(), result.success(), duration);

                return Optional.of(result);
            }
        }

        return Optional.empty();
    }
}
