package dev.mark.task.data;

import dev.mark.agent.AgentStatus;
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

@Entity
@Table(name = "tasks")
public class TaskEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 2000)
    private String goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentStatus status;

    @Column(columnDefinition = "TEXT")
    private String finalAnswer;

    @Column(nullable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "task_steps", joinColumns = @JoinColumn(name = "task_id"))
    private List<AgentStepEmbeddable> steps = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "task_plan", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "step", columnDefinition = "TEXT")
    private List<String> plan = new ArrayList<>();

    public TaskEntity() {}

    public TaskEntity(UUID id, String goal, AgentStatus status, String finalAnswer, Instant createdAt) {
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
    public AgentStatus getStatus() { return status; }
    public void setStatus(AgentStatus status) { this.status = status; }
    public String getFinalAnswer() { return finalAnswer; }
    public void setFinalAnswer(String finalAnswer) { this.finalAnswer = finalAnswer; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public List<AgentStepEmbeddable> getSteps() { return steps; }
    public void setSteps(List<AgentStepEmbeddable> steps) { this.steps = steps; }
    public List<String> getPlan() { return plan; }
    public void setPlan(List<String> plan) { this.plan = plan; }
}
