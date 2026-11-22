# MARK engineering rules

## Product direction

MARK has a cloud-hosted modular Spring Boot backend and a separate local Windows Device Runtime. The runtime initiates an authenticated outbound connection to the cloud and executes only explicitly allowed device commands.

## Keep the code small

- Prefer direct, readable code over speculative frameworks and abstraction layers.
- Do not create microservices for backend modules.
- Do not add a dependency unless it solves a current requirement.
- Do not add an interface with only one implementation unless it creates a real boundary (for example, an external provider or device transport).
- Do not create generic engines, ASTs, transition analyzers, or plugin systems without a demonstrated use case.
- Keep methods focused and data contracts explicit.
- Delete dead code instead of preserving speculative compatibility.

## Architecture boundaries

- User owns profile and user-configurable preferences.
- Task owns task records, lifecycle status, activity events, and final results.
- Agent owns planning, orchestration, tool selection/execution coordination, verification, and recovery.
- Model owns model/provider metadata, routing, and inference adapters.
- Cloud Device module owns pairing, device registry, connection state, and command dispatch.
- Device Runtime owns local Windows execution and local permissions.
- Memory is deferred until a concrete use case and data lifecycle are designed.

## UI rules

- Three columns: task list, main activity stream, collapsible context panel.
- One chronological activity stream; no separate planning, thoughts, or trace pages.
- Show concise user-facing status and outcomes, not private chain-of-thought.
- Long tool output may be expanded on demand.
- Keep the prompt composer compact.

## Safety and reliability

- Never expose a Windows device port directly to the public internet.
- Device Runtime connects outward using authenticated WSS.
- Validate every command locally before execution.
- Do not execute arbitrary shell strings supplied by an LLM.
- Require explicit approval for consequential actions.
- Use command IDs, expiry, and duplicate protection for side-effecting commands.
- Never claim a task succeeded without evidence.

## Changes

- Add tests for meaningful behavior.
- Explain the files changed, runtime flow, and how to test each milestone.
- Do not silently make broad architectural changes.
