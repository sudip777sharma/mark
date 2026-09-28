package dev.mark.task.entity;

import dev.mark.agent.model.AgentIntentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "interaction_messages")
public class InteractionMessageEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "user_input", columnDefinition = "TEXT", nullable = false)
    private String userInput;

    @Enumerated(EnumType.STRING)
    @Column(name = "intent", nullable = false)
    private AgentIntentType intent;

    @Column(name = "reply", columnDefinition = "TEXT")
    private String reply;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public InteractionMessageEntity() {
    }

    public InteractionMessageEntity(String userInput, AgentIntentType intent, String reply) {
        this.userInput = userInput;
        this.intent = intent;
        this.reply = reply;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUserInput() {
        return userInput;
    }

    public void setUserInput(String userInput) {
        this.userInput = userInput;
    }

    public AgentIntentType getIntent() {
        return intent;
    }

    public void setIntent(AgentIntentType intent) {
        this.intent = intent;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
