package dev.mark.task.entity;

import dev.mark.agent.model.AgentStatusModel;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
* - What the class does: Represents a task entity mapped to the database, capturing the core details, status, execution steps, and plans for agent-driven operations.
* - Why it is useful: Provides a persistent data structure to track, store, and retrieve the lifecycle, planning, and results of complex agent tasks.
* - How it fits in the flow of the application: Acts as the primary data model for the persistence layer, used by repositories and services to save and load task states during execution.
* - Its methods and variables and how they are useful: Contains fields like id, goal, status, finalAnswer, createdAt, steps, and plan to hold task data, along with standard getters and setters to access and modify these properties.
* - Its logic and how it gets fit into the overall application logic: Maps directly to the tasks table via JPA annotations, allowing the application to persist operational states and history for auditing and progress tracking.
*/

@Entity
@Table(name = "tasks")
public class TaskEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 2000)
    private String goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentStatusModel status;

    @Column(columnDefinition = "TEXT")
    private String finalAnswer;

    @Column
    private Long configId;

    @Column(columnDefinition = "TEXT")
    private String currentAction;

    @Column(nullable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "task_steps", joinColumns = @JoinColumn(name = "task_id"))
    @AttributeOverrides({
        @AttributeOverride(name = "description", column = @Column(columnDefinition = "TEXT")),
        @AttributeOverride(name = "outcome", column = @Column(columnDefinition = "TEXT"))
    })
    private List<AgentStepEmbeddableEntity> steps = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "task_plan", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "step", columnDefinition = "TEXT")
    private List<String> plan = new ArrayList<>();

    public TaskEntity() {}

    public TaskEntity(UUID id, String goal, AgentStatusModel status, String finalAnswer, Instant createdAt) {
        this.id = id;
        this.goal = goal;
        this.status = status;
        this.finalAnswer = finalAnswer;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }
    public AgentStatusModel getStatus() { return status; }
    public void setStatus(AgentStatusModel status) { this.status = status; }
    public String getFinalAnswer() { return finalAnswer; }
    public void setFinalAnswer(String finalAnswer) { this.finalAnswer = finalAnswer; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Long getConfigId() { return configId; }
    public String getCurrentAction() { return currentAction; }
    public void setCurrentAction(String currentAction) { this.currentAction = currentAction; }
    public void setConfigId(Long configId) { this.configId = configId; }
    public List<AgentStepEmbeddableEntity> getSteps() { return steps; }
    public void setSteps(List<AgentStepEmbeddableEntity> steps) { this.steps = steps; }
    public List<String> getPlan() { return plan; }
    public void setPlan(List<String> plan) { this.plan = plan; }
}
