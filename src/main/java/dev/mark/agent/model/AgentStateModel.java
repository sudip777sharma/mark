package dev.mark.agent.model;

import dev.mark.llm.dto.LlmMessageDTO;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * - Represents the core execution state for an autonomous agent task.
 * - Useful for maintaining centralized data consistency and tracking task
 * progress throughout execution.
 * - Acts as the primary state container passed between planning, execution, and
 * LLM interaction components.
 * - Contains fields for task identifiers, goals, steps, LLM conversation
 * history, plans, statuses, and world states, along with modifier methods to
 * update them.
 * - Integrates into the application logic as the mutable data backbone that
 * transitions the agent from initialization to the final answer.
 */

public final class AgentStateModel {
    private final UUID taskId;
    private final String goal;
    private final Long configId;
    private final Instant createdAt;
    private final List<AgentStepModel> steps = new ArrayList<>();
    private final List<LlmMessageDTO> messages = new ArrayList<>();
    private AgentStatusModel status = AgentStatusModel.CREATED;
    private List<String> plan = new ArrayList<>();
    private String finalAnswer;
    private String currentAction;
    private AgentWorldStateModel latestWorldState = AgentWorldStateModel.empty();

    public AgentStateModel(String taskId, String goal, Long configId) {
        this.taskId = UUID.fromString(taskId);
        this.goal = goal;
        this.configId = configId;
        this.createdAt = Instant.now();
    }

    public Long configId() {
        return configId;
    }

    public void setStatus(String statusStr) {
        this.status = AgentStatusModel.valueOf(statusStr);
    }

    public void addStep(AgentStepModel step) {
        steps.add(step);
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }

    public void addMessage(LlmMessageDTO message) {
        this.messages.add(message);
    }

    public void updateWorldState(AgentWorldStateModel worldState) {
        this.latestWorldState = worldState;
    }

    public String taskId() {
        return taskId.toString();
    }

    public String goal() {
        return goal;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String status() {
        return status.name();
    }

    public List<AgentStepModel> steps() {
        return List.copyOf(steps);
    }

    public List<String> plan() {
        return List.copyOf(plan);
    }

    public void setPlan(List<String> plan) {
        this.plan = new ArrayList<>(plan);
    }

    public String finalAnswer() { return finalAnswer; }
    public String currentAction() { return currentAction; }
    public void setCurrentAction(String currentAction) { this.currentAction = currentAction; }

    public List<LlmMessageDTO> messages() {
        return List.copyOf(messages);
    }

    public AgentWorldStateModel latestWorldState() {
        return latestWorldState;
    }
}
