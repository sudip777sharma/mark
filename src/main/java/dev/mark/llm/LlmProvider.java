package dev.mark.llm;

public interface LlmProvider {
    String name();
    LlmResponse complete(LlmRequest request);
    PlanResponse plan(LlmRequest request);
}
