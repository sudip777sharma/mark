package dev.mark.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AgentState {
    private final UUID taskId;
    private final String goal;
    private final Instant createdAt;
    private final List<AgentStep> steps = new ArrayList<>();
    private AgentStatus status = AgentStatus.CREATED;
    private List<String> plan = new ArrayList<>();
    private String finalAnswer;

    public AgentState(UUID taskId, String goal) {
        this.taskId = taskId;
        this.goal = goal;
        this.createdAt = Instant.now();
    }
    public void transitionTo(AgentStatus next) {
        if (status == AgentStatus.COMPLETED || status == AgentStatus.FAILED) throw new IllegalStateException("A terminal task cannot transition");
        status = next;
    }
    public void addStep(AgentStep step) { steps.add(step); }
    public void setFinalAnswer(String finalAnswer) { this.finalAnswer = finalAnswer; }
    public UUID taskId() { return taskId; }
    public String goal() { return goal; }
    public Instant createdAt() { return createdAt; }
    public AgentStatus status() { return status; }
    public List<AgentStep> steps() { return List.copyOf(steps); }
    public List<String> plan() { return List.copyOf(plan); }
    public void setPlan(List<String> plan) { this.plan = new ArrayList<>(plan); }
    public String finalAnswer() { return finalAnswer; }
}
