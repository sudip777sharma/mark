package dev.mark.task.data;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

@Embeddable
public class AgentStepEmbeddable {
    private int stepNumber;
    private String description;
    private String toolName;
    @Column(columnDefinition = "TEXT")
    private String outcome;
    private String provider;

    public AgentStepEmbeddable() {}

    public AgentStepEmbeddable(int stepNumber, String description, String toolName, String outcome, String provider) {
        this.stepNumber = stepNumber;
        this.description = description;
        this.toolName = toolName;
        this.outcome = outcome;
        this.provider = provider;
    }

    public int getStepNumber() { return stepNumber; }
    public void setStepNumber(int stepNumber) { this.stepNumber = stepNumber; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentStepEmbeddable that = (AgentStepEmbeddable) o;
        return stepNumber == that.stepNumber && Objects.equals(description, that.description) && Objects.equals(toolName, that.toolName) && Objects.equals(outcome, that.outcome) && Objects.equals(provider, that.provider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stepNumber, description, toolName, outcome, provider);
    }
}

