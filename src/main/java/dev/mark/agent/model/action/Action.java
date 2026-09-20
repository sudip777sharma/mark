package dev.mark.agent.model.action;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ClickAction.class, name = "click"),
    @JsonSubTypes.Type(value = TypeAction.class, name = "type"),
    @JsonSubTypes.Type(value = VerifyAction.class, name = "verify"),
    @JsonSubTypes.Type(value = WaitAction.class, name = "wait")
})
public interface Action {
    String type();
    boolean requiresTarget();
}
