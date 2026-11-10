package dev.mark.trajectory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Persists verified ground-truth task execution trajectories.
 * Provides the empirical dataset for the Ultra Instinct skill compiler,
 * allowing routine tasks to execute in <50ms without invoking LLMs.
 */
@Entity
@Table(name = "execution_trajectories")
public class ExecutionTrajectoryEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String taskId;

    @Column(nullable = false, length = 2000)
    private String goal;

    @Column(nullable = false)
    private String intent;

    @Column(columnDefinition = "TEXT")
    private String extractedSlotsJson;

    @Column(columnDefinition = "TEXT")
    private String toolSequenceJson;

    @Column(nullable = false)
    private boolean verified;

    @Column(nullable = false)
    private long executionDurationMs;

    @Column(nullable = false)
    private int llmRequestCount;

    @Column(nullable = false)
    private Instant createdAt;

    public ExecutionTrajectoryEntity() {}

    public ExecutionTrajectoryEntity(
            UUID id,
            String taskId,
            String goal,
            String intent,
            String extractedSlotsJson,
            String toolSequenceJson,
            boolean verified,
            long executionDurationMs,
            int llmRequestCount,
            Instant createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.goal = goal;
        this.intent = intent;
        this.extractedSlotsJson = extractedSlotsJson;
        this.toolSequenceJson = toolSequenceJson;
        this.verified = verified;
        this.executionDurationMs = executionDurationMs;
        this.llmRequestCount = llmRequestCount;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }

    public String getExtractedSlotsJson() { return extractedSlotsJson; }
    public void setExtractedSlotsJson(String extractedSlotsJson) { this.extractedSlotsJson = extractedSlotsJson; }

    public String getToolSequenceJson() { return toolSequenceJson; }
    public void setToolSequenceJson(String toolSequenceJson) { this.toolSequenceJson = toolSequenceJson; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public long getExecutionDurationMs() { return executionDurationMs; }
    public void setExecutionDurationMs(long executionDurationMs) { this.executionDurationMs = executionDurationMs; }

    public int getLlmRequestCount() { return llmRequestCount; }
    public void setLlmRequestCount(int llmRequestCount) { this.llmRequestCount = llmRequestCount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
