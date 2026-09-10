# MARK

MARK (codename MARK-45) is a local-first autonomous-agent prototype. It combines an LLM-driven orchestration loop with bounded tool execution for filesystem, browser, desktop, and system tasks.

## Run

Requires Java 21 and Maven Wrapper.

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"
```

Run llama.cpp on `127.0.0.1:8080`, then start MARK on `8081` and create a task:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/tasks -ContentType application/json -Body '{"goal":"Summarize the project status"}'
```

MARK sends the goal, bounded history, current environment state, and available tool schemas to the configured provider. Tool results are persisted to the local H2 development database and become observations for later iterations.

## Multi-step agent loop

`AgentOrchestratorService` retains typed conversation history: prior tool calls, structured tool observations, and assistant completion content. It repeats model selection and tool execution for up to `mark.agent.max-steps` (default `30`). An explicit model completion must pass `AgentCompletionVerifierService`; tool errors and unknown tool names become observations so the model can re-plan on the next iteration.

## Architecture

`TaskController` delegates to `AgentOrchestratorService`. The orchestrator owns task-state transitions and calls tools through `ToolRegistry`. `LlmRouterService` routes prompts to the configured provider. Tool results carry the observation used by verification.

The next architecture milestone is structured UI observation: stable UI-element identity, scoped snapshots, and transition-aware action validation. Do not treat the currently available filesystem, command, browser, or desktop tools as unrestricted automation; consequential operations require explicit user approval.
