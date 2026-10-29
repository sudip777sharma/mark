package dev.mark.task.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "llm_interaction_logs")
public class LlmInteractionLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String taskId;
    
    private String phase;

    @Column(columnDefinition = "TEXT")
    private String prompt;
    
    @Column(columnDefinition = "TEXT")
    private String thinking;
    
    @Column(columnDefinition = "TEXT")
    private String response;

    private Instant createdAt = Instant.now();

    public LlmInteractionLogEntity() {}

    public LlmInteractionLogEntity(String taskId, String phase, String prompt, String thinking, String response) {
        this.taskId = taskId;
        this.phase = phase;
        this.prompt = prompt;
        this.thinking = thinking;
        this.response = response;
    }

    public Long getId() { return id; }
    public String getTaskId() { return taskId; }
    public String getPhase() { return phase; }
    public String getPrompt() { return prompt; }
    public String getThinking() { return thinking; }
    public String getResponse() { return response; }
    public Instant getCreatedAt() { return createdAt; }

    public void setTaskId(String taskId) { this.taskId = taskId; }
    public void setPhase(String phase) { this.phase = phase; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public void setThinking(String thinking) { this.thinking = thinking; }
    public void setResponse(String response) { this.response = response; }
}
