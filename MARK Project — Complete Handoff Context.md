# MARK — Local-First Autonomous AI Agent

You are taking over an existing software project called **MARK**.

## 1. Vision

MARK is intended to become a personal, general-purpose autonomous digital agent.

The long-term goal:

A user gives MARK a high-level goal, for example:

> "Find suitable Java backend jobs and apply to them."

MARK should eventually be able to:

- understand the goal
- plan multi-step work
- use tools
- operate websites/browser applications
- work with files
- use terminal/system tools
- observe results
- adapt when things change
- recover from failures
- ask the user when necessary information is missing
- verify that the intended result actually happened
- remember useful information
- operate primarily with local LLM inference
- optionally fall back to cloud LLM providers

The ultimate target is roughly **80%+ verified success on a defined set of real-world workflows**, not 80% on arbitrary computer activity.

## 2. Core architectural principle

MARK is NOT supposed to be "one giant LLM that does everything."

The intended architecture is:

User Goal
→ Agent Engine
→ LLM / Planner
→ Tool Selection
→ Tool Execution
→ Observation
→ Verification
→ Re-plan / Continue
→ Completion

The LLM should be treated as the reasoning component.

Deterministic software should be used whenever possible.

Examples:

- calculation → normal code
- database queries → SQL
- file operations → filesystem APIs
- browser DOM interaction → browser automation
- APIs → direct HTTP/API calls
- screenshot interaction → vision model only when structured access is unavailable

## 3. Local-first principle

Local inference is the default.

The project must NOT require paid third-party LLM APIs.

However, the architecture must support optional cloud fallback later:

- Google Gemini
- OpenAI
- Anthropic/Claude
- other providers

The application should use an abstraction such as:

LlmProvider
→ LocalLlmProvider
→ GeminiProvider
→ OpenAIProvider
→ AnthropicProvider

The agent should not depend on one provider.

The future goal is to route intelligently:

simple task → local
known task → local
difficult task → stronger local model
uncertain / failed task → optional cloud fallback

## 4. User hardware

Development machine:

- Windows 11
- Intel Core i7-13620H
- 16 GB RAM
- Intel UHD integrated graphics
- No discrete GPU

Because of this, local models must initially be relatively small and CPU-friendly.

## 5. Current local LLM

We installed:

llama.cpp

Current local model:

`ggml-org/gemma-4-E4B-it-GGUF:Q8_0`

Runtime:

llama.cpp HTTP server

Typical command:

```powershell
cd C:\AI
.\llama-server.exe -hf ggml-org/gemma-4-E4B-it-GGUF:Q8_0 --jinja --reasoning off -c 8192 -np 1
```

Server:

`http://127.0.0.1:8080`

We verified that the model can perform structured tool calling through:

`POST /v1/chat/completions`

Example tool:

`search_web(query)`

Gemma successfully returned a structured tool call:

```json
{
  "name": "search_web",
  "arguments": "{\"query\":\"Java backend jobs\"}"
}
```

Approximate CPU generation speed observed:

~8 tokens/sec

This is usable for development but will be slow for long workflows.

## 6. Current backend technology

MARK backend:

- Java 21
- Spring Boot 3.5.0
- Maven
- Maven Wrapper

Project root:

`C:\Users\sudip\OneDrive\Desktop\mark`

Note:
We originally discussed Spring Boot 4.x, but the actual repository currently uses Spring Boot 3.5.0. Do not upgrade just for the sake of upgrading.

## 7. Current repository structure

Conceptually:

```text
mark/
├── AGENTS.md
├── README.md
├── pom.xml
├── mvnw
├── mvnw.cmd
├── .mvn/
└── src/
    ├── main/
    │   ├── java/dev/mark/
    │   │   ├── MarkApplication.java
    │   │   ├── agent/
    │   │   ├── llm/
    │   │   ├── task/
    │   │   └── tool/
    │   └── resources/
    │       └── application.yml
    └── test/
        └── java/dev/mark/
            ├── agent/
            ├── llm/
            ├── task/
            └── tool/
```

Generated `target/` artifacts are ignored by Git.

## 8. Important existing abstractions

### TaskController

REST entry point.

Current endpoint:

`POST /api/tasks`

Example:

```json
{
  "goal": "Say hello to MARK"
}
```

### AgentEngine

Core orchestration engine.

Current behavior:

Goal
→ LLM
→ Tool Call
→ Tool Execution
→ Observation
→ LLM again
→ next tool or completion
→ Verification

It is bounded by a configurable maximum step count.

Default max steps:

10

### AgentState / AgentStep

Track task lifecycle and execution steps.

### LlmProvider

Provider-independent model abstraction.

### LlmRouter

Chooses LLM provider.

Currently local provider is the actual implementation.

### LocalLlmProvider

Calls llama.cpp's OpenAI-compatible API:

`http://127.0.0.1:8080/v1/chat/completions`

Supports:

- system messages
- user messages
- tool definitions
- tool_choice
- structured tool calls
- response parsing

### Tool

Generic tool interface.

### ToolRegistry

Keeps track of available tools and resolves tool names.

### ToolResult

Represents tool execution results.

### CompletionVerifier

MARK does NOT automatically trust the LLM saying "done".

Completion requires successful tool evidence / verification.

### LlmMessage

Structured conversation history.

### LlmToolObservation

Represents structured tool output returned to the LLM on subsequent iterations.

## 9. Current tools

Real tool:

### EchoTool

Simple deterministic tool used to prove the architecture.

Test-only tools:

### IncrementTool

Input integer and returns value + 1.

### FinishTool

Used to terminate deterministic multi-step tests.

These test tools proved the generic multi-step loop.

## 10. Current verified behavior

We ran:

```powershell
.\mvnw.cmd clean verify
```

Result:

```text
Tests run: 17
Failures: 0
Errors: 0
BUILD SUCCESS
```

The tests verify:

- successful multi-step execution
- tool failure recovery
- unknown-tool recovery
- maximum-step enforcement
- malformed LLM response handling
- unavailable LLM handling
- structured observation history
- existing state/tool/provider/controller behavior

Example verified conceptual flow:

```text
increment
→ increment
→ finish
→ COMPLETED
```

Recovery example:

```text
unknown tool
→ failure observation
→ alternate action
→ finish
→ COMPLETED
```

Maximum step example:

```text
increment
→ increment
→ ...
→ Maximum agent steps exceeded
→ FAILED
```

## 11. Current Git checkpoints

Repository history currently contains:

```text
3b955c8 feat: add bounded multi-step agent loop
f29bf8c chore: ignore build artifacts
b0af98c feat: establish real local LLM driven agent loop
```

The repository was clean at the latest checkpoint.

## 12. What MARK can do RIGHT NOW

MARK can currently:

- receive a task through its REST API
- send the task to the local Gemma model
- give the model available tool definitions
- receive structured tool calls
- execute registered tools
- send observations back to the model
- perform multiple bounded tool/LLM iterations
- recover from some tool failures
- enforce a maximum number of steps
- verify completion

Example current task:

```json
{
  "goal": "Say hello to MARK"
}
```

Current architecture allows:

```text
POST /api/tasks
→ TaskController
→ AgentEngine
→ LlmRouter
→ LocalLlmProvider
→ llama.cpp
→ Gemma
→ Tool call
→ ToolRegistry
→ Tool
→ Observation
→ AgentEngine
→ Gemma again
→ CompletionVerifier
```

## 13. What MARK CANNOT DO YET

It does NOT yet have:

- Playwright/browser tools
- browser navigation
- browser clicking
- browser typing
- DOM extraction
- screenshot/vision computer control
- desktop control
- filesystem tools
- terminal/shell tools
- calculator tool
- SQLite persistence
- vector DB
- RAG
- persistent user memory
- trajectory learning
- fine-tuning
- cloud providers
- provider fallback routing
- job-search workflow
- job-application workflow

These must be added incrementally.

## 14. Important development philosophy

Do NOT try to implement the entire coworker in one shot.

Work in small milestones.

Each milestone should:

1. modify only what is necessary
2. preserve existing abstractions
3. add tests
4. run `.\mvnw.cmd clean verify`
5. ensure BUILD SUCCESS
6. update README when appropriate
7. commit to Git
8. stop

Avoid overengineering.

Do not introduce LangChain/LangGraph/etc. unless we later determine that they provide a compelling advantage.

We want to understand the architecture ourselves.

## 15. Planned technology stack

Core:

- Java 21
- Spring Boot
- Maven

LLM:

- llama.cpp
- local GGUF models

Browser:

- Playwright Java

Persistence:

- SQLite initially

Semantic memory later:

- Qdrant or another local vector database

Optional cloud models later:

- Gemini
- OpenAI
- Anthropic

## 16. Planned roadmap

Current status:

Phase 1:
✅ Spring Boot foundation

Phase 2:
✅ Local LLM integration

Phase 3:
✅ Real LLM-driven tool selection

Phase 4:
✅ Bounded multi-step agent loop

Next:

Phase 5:
→ real deterministic tools

Recommended first:
CalculatorTool

Then:

Phase 6:
→ filesystem tools

Phase 7:
→ browser tools using Playwright

Phase 8:
→ browser observation + verification

Phase 9:
→ screenshot/vision fallback

Phase 10:
→ persistent SQLite task/history state

Phase 11:
→ memory/RAG

Phase 12:
→ cloud provider abstraction + fallback

Phase 13:
→ evaluation framework

Phase 14:
→ job research workflow

Phase 15:
→ job application workflow

Phase 16:
→ trajectory learning / distillation / small specialist models

## 17. Important architecture for browser work

When browser capabilities are added, use a hierarchy:

1. direct API when available
2. DOM/accessibility/structured browser state
3. Playwright interaction
4. screenshot + vision model when structured access isn't sufficient

Do NOT make screenshot clicking the default approach.

## 18. Long-term memory design

Do not put everything into a vector database.

Use:

Structured memory:
SQLite

Semantic memory:
vector DB

Episodic memory:
task/trajectory history

User facts and preferences should usually be stored as memory, not fine-tuned into the model.

Fine-tuning/distillation can later be used to teach recurring behavior, especially after collecting successful trajectories.

## 19. Reliability target

The long-term target is approximately:

80%+ verified completion on a defined suite of real workflows.

Important metrics:

- task success
- false completion rate
- recovery rate
- human intervention rate
- average steps
- latency
- tokens
- memory usage
- CPU/GPU usage
- consistency across repeated runs

Do not interpret generic LLM benchmark scores as MARK reliability.

## 20. Security principles

Eventually tools need permissions.

Examples:

Read:
automatic

Create/edit:
configurable

Send email:
confirmation

Purchase:
confirmation

Delete:
confirmation

Shell:
sandbox/controlled

Credentials:
never expose directly to the LLM

Websites, email, PDFs and tool outputs must be considered untrusted input because of prompt injection.

## 21. Current development machine setup

Java:

```powershell
java -version
```

works with:

```text
OpenJDK 21.0.12 Temurin
```

JAVA_HOME currently used:

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot"
```

MARK typically runs on port 8081 while llama.cpp runs on port 8080.

MARK:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--server.port=8081'
```

llama.cpp:

```powershell
cd C:\AI
.\llama-server.exe -hf ggml-org/gemma-4-E4B-it-GGUF:Q8_0 --jinja --reasoning off -c 8192 -np 1
```

## 22. Immediate next milestone

Do NOT jump to browser automation yet.

First add one useful deterministic tool, preferably:

CalculatorTool

This will demonstrate:

user goal
→ LLM selects calculator
→ calculator executes deterministically
→ observation
→ LLM decides next action/completion
→ verification

Then proceed to browser.

## 23. How to work with the human developer

The developer wants to understand the code, not blindly accept AI-generated code.

For every milestone:

- explain the classes touched
- explain the runtime flow
- explain why the abstraction exists
- explain how to manually test it
- provide Postman/PowerShell examples
- provide exact commands to run
- explicitly state what MARK can and cannot do after the milestone

Do not silently make large architectural changes.

Do not implement future milestones unless explicitly requested.

## 24. Current objective

Continue from the existing Git state.

Do not recreate the project.

Do not replace the architecture.

Do not restart from scratch.

Inspect the existing repository first.

Then propose the smallest next change needed to implement the CalculatorTool milestone.