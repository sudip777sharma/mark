# MARK

MARK (codename MARK-45) is a local-first autonomous AI agent. It currently provides a bounded, local-LLM-driven tool loop; it is not yet a browser or desktop agent.

## Run

Requires Java 21 and Maven Wrapper.

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"
```

Create and execute an agent task:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/tasks -ContentType application/json -Body '{"goal":"Summarize the project status"}'
```

Run llama.cpp on port `8080` and MARK on a different port (for example `8081`). MARK sends the goal and available tool schemas to the local model, executes at most one selected tool per iteration, returns structured observations to the model, and verifies the model's final completion against successful tool evidence.

## Multi-step agent loop

`AgentEngine` retains typed conversation history: prior tool calls, structured tool observations, and assistant completion content. It repeats model selection and tool execution for up to `mark.agent.max-steps` (default `10`). An explicit model completion must pass `CompletionVerifier`; a completion without successful tool evidence fails. Tool errors and unknown tool names become observations so the model can re-plan on the next iteration.

## Architecture

`TaskController` delegates to `AgentEngine`. The engine owns task-state transitions and calls tools through `ToolRegistry`. `LlmRouter` routes prompts to the configured `LocalLlmProvider`. Tool results carry the observation used by verification.

Future milestones may add SQLite task persistence, additional tools, richer verification, permissions, and optional cloud providers.
