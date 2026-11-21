package dev.mark.agent.model;

import java.util.List;

/**
 * Structured representation of a plan step.
 * Replaces unstructured string lists with typed objectives, preconditions,
 * expected outcomes, and verification criteria.
 */
public record PlanStepModel(
        int index,
        String title,
        String objective,
        List<String> preconditions,
        String expectedOutcome,
        String verificationRequirement,
        PlanStepStatus status
) {
    public enum PlanStepStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        SKIPPED
    }

    public static PlanStepModel fromString(int index, String description) {
        String cleanDesc = description != null ? description.trim() : "";
        return new PlanStepModel(
                index,
                cleanDesc,
                cleanDesc,
                List.of(),
                "Outcome verified",
                "Tool postconditions verified",
                PlanStepStatus.PENDING
        );
    }

    public PlanStepModel withStatus(PlanStepStatus newStatus) {
        return new PlanStepModel(index, title, objective, preconditions, expectedOutcome, verificationRequirement, newStatus);
    }
}
