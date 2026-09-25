package dev.mark.task.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

    /**
    - **Purpose**: Represents an embedded JPA entity that stores individual execution steps performed by an agent.
    - **Utility**: Encapsulates step-specific details to maintain structured audit trails within parent entities without requiring separate table mappings.
    - **Application Flow**: Acts as a value object integrated into parent task or execution entities to capture sequential progress during persistence.
    - **Components**:
      - Fields (stepNumber, description, toolName, outcome, provider) track the sequence, action, tool, result, and provider.
      - Getters/Setters handle data access and mutation.
      - equals/hashCode ensure reliable value-based comparisons.
    - **Logic**: Fits into the persistence layer by mapping complex agent workflows directly into parent entity columns as reusable embeddable components.
    */

/**
- **What it does**: Represents an embedded JPA entity that stores individual execution steps performed by an agent.
- **Why it is useful**: Encapsulates step-specific execution details cleanly, allowing parent task entities to persist structured audit trails without requiring separate table mappings.
- **Application flow**: Integrated directly into parent task or execution entities as a value object to capture sequential progress within the persistence tier.
- **Methods and variables**:
  - Fields (stepNumber, description, toolName, outcome, provider) track the sequence, actions, tools, results, and execution providers of each step.
  - Getters and setters provide data access and mutation.
  - equals and hashCode ensure reliable value-based comparisons for collections.
- **Application logic**: Fits into the data persistence layer by mapping complex, multi-step agent workflows directly into database columns of parent entities as reusable embeddable components.
*/

@Embeddable
public class AgentStepEmbeddableEntity {
    private int stepNumber;
    @Column(columnDefinition = "TEXT")
    private String description;
    private String toolName;
    @Column(columnDefinition = "TEXT")
    private String toolArguments;
    @Column(columnDefinition = "TEXT")
    private String outcome;
    private String provider;

    public AgentStepEmbeddableEntity() {}

    public AgentStepEmbeddableEntity(int stepNumber, String description, String toolName, String toolArguments, String outcome, String provider) {
        this.stepNumber = stepNumber;
        this.description = description;
        this.toolName = toolName;
        this.toolArguments = toolArguments;
        this.outcome = outcome;
        this.provider = provider;
    }

    public int getStepNumber() { return stepNumber; }
    public void setStepNumber(int stepNumber) { this.stepNumber = stepNumber; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getToolArguments() { return toolArguments; }
    public void setToolArguments(String toolArguments) { this.toolArguments = toolArguments; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentStepEmbeddableEntity that = (AgentStepEmbeddableEntity) o;
        return stepNumber == that.stepNumber && Objects.equals(description, that.description) && Objects.equals(toolName, that.toolName) && Objects.equals(toolArguments, that.toolArguments) && Objects.equals(outcome, that.outcome) && Objects.equals(provider, that.provider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stepNumber, description, toolName, toolArguments, outcome, provider);
    }
}
