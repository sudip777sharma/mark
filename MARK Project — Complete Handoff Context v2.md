# MARK — Local-First Autonomous AI Agent (Version 2)

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
REST entry point. Current endpoint: `POST /api/tasks`

### AgentEngine / AgentOrchestratorService
Core orchestration engine. It is bounded by a configurable maximum step count (default 10).

### AgentState / AgentStep
Track task lifecycle and execution steps. (Uses embedded JPA entities for persistence design).

### LlmProvider & LlmRouter
Provider-independent model abstraction. Chooses LLM provider (Local/Gemini/OpenAI).

### LocalLlmProvider (OpenAiCompatibleProviderService)
Calls llama.cpp's OpenAI-compatible API (`http://127.0.0.1:8080/v1/chat/completions`). Supports structured tool calls and system messages.

### Tool & ToolRegistry
Generic tool interface and registry to keep track of available tools.

### CompletionVerifier (AgentCompletionVerifierService)
MARK does NOT automatically trust the LLM saying "done". Completion requires successful tool evidence / verification.

### SafetyService (AgentSafetyService)
Evaluates LLM tool calls for potential security risks before execution.

## 9. Current tools (Version 2 Updates)

We have successfully implemented a robust suite of real-world tools across different domains:

**System Tools**
- `CalculatorTool`: Evaluates math expressions.
- `AskUserTool`: Pauses execution to ask the human for input or confirmation.
- `ExecuteCommandTool`: Runs local shell/terminal commands.
- `TaskCompleteTool`: Explicit tool to signal task success.
- `EchoTool`: Simple deterministic tool used for testing.

**File System Tools**
- `ReadFileTool`: Reads local files.
- `WriteFileTool`: Creates or overwrites files.
- `DeleteFileTool`: Deletes files.
- `ReplaceInFileTool`: Modifies existing file content.
- `SearchFileContentTool`: Searches for patterns in files.
- `ListDirectoryTool`: Lists contents of a directory.

**Browser Tools (Playwright)**
- `BrowserNavigateTool`: Navigates to URLs.
- `BrowserReadPageTool`: Extracts DOM/page content.
- `BrowserClickTool`: Clicks elements.
- `BrowserTypeTool`: Types into input fields.
- `BrowserExtractTool`: Extracts specific data points from the page.

**Desktop Automation Tools**
- `DesktopAutomationTool`: Uses Java AWT Robot for UI automation.
- `InspectUiTool`: Inspects the active desktop window (via OS APIs/PowerShell).
- `OcrTool`: Performs Optical Character Recognition on the screen.

## 10. Current verified behavior

The tests verify:
- successful multi-step execution
- tool failure recovery & unknown-tool recovery
- maximum-step enforcement
- malformed LLM response handling
- unavailable LLM handling
- structured observation history
- **(NEW)** Local file manipulation, browser automation via Playwright, and desktop UI interaction.

## 11. Current Git checkpoints

Repository has advanced past the initial multi-step agent loop.
Major systems (Browser, File, System, Desktop tools) are now implemented and active.

## 12. What MARK can do RIGHT NOW

MARK can currently:
- receive a task through its REST API
- send the task to the local Gemma model (or others)
- perform multiple bounded tool/LLM iterations
- recover from some tool failures and enforce maximum steps
- verify completion and check safety guardrails
- **(NEW)** Read, write, and search the local filesystem.
- **(NEW)** Run terminal/shell commands.
- **(NEW)** Automate the browser (navigate, click, type, extract).
- **(NEW)** Automate the desktop UI and use OCR.
- **(NEW)** Pause and ask the user for input/feedback.

## 13. What MARK CANNOT DO YET

It does NOT yet have:
- SQLite/Postgres task persistence (Entity models exist, but full database integration is pending)
- vector DB
- RAG (Retrieval-Augmented Generation)
- persistent user memory
- trajectory learning
- fine-tuning
- job-search workflow
- job-application workflow
- Client-Server split (Desktop App frontend communicating with a Hosted Gateway)

## 14. Important development philosophy

Do NOT try to implement the entire coworker in one shot.
Work in small milestones. Avoid overengineering.
Do not introduce LangChain/LangGraph/etc. unless we later determine that they provide a compelling advantage.

## 15. Planned technology stack

Core: Java 21, Spring Boot, Maven
LLM: llama.cpp (local GGUF models), OpenAI/Gemini (fallback)
Browser: Playwright Java
Persistence: SQLite (relational), Vector DB (semantic)
**Frontend (Planned): React + Vite + Tauri (Thick Client Desktop App)**

## 16. Planned roadmap

Current status:

Phase 1: ✅ Spring Boot foundation
Phase 2: ✅ Local LLM integration
Phase 3: ✅ Real LLM-driven tool selection
Phase 4: ✅ Bounded multi-step agent loop
Phase 5: ✅ real deterministic tools (Calculator, Execute Command)
Phase 6: ✅ filesystem tools
Phase 7: ✅ browser tools using Playwright
Phase 8: ✅ browser observation + verification
Phase 9: ✅ screenshot/vision fallback (OCR / Desktop Automation)

Next:

Phase 10: → persistent SQLite task/history state
Phase 11: → memory/RAG
Phase 12: → cloud provider abstraction + fallback
Phase 13: → evaluation framework
Phase 14: → job research workflow
Phase 15: → job application workflow
Phase 16: → trajectory learning / distillation / small specialist models
**Phase 17 (NEW)**: → Separate frontend (Tauri/React) from backend LLM gateway.

## 17. Important architecture for browser work

When browser capabilities are used, use a hierarchy:
1. direct API when available
2. DOM/accessibility/structured browser state
3. Playwright interaction
4. screenshot + vision model when structured access isn't sufficient

## 18. Long-term memory design

Use:
- Structured memory: SQLite
- Semantic memory: vector DB
- Episodic memory: task/trajectory history

## 19. Reliability target
80%+ verified completion on a defined suite of real workflows.

## 20. Security principles
Websites, email, PDFs and tool outputs must be considered untrusted input because of prompt injection.

## 21. Current development machine setup
Java: OpenJDK 21.0.12 Temurin
MARK typically runs on port 8081. llama.cpp on 8080.

## 22. Immediate next milestone

The immediate next goals are:
1. **Phase 10: Task Persistence.** Implementing the SQLite database to store task history, trajectories, and states (`AgentStepEmbeddableEntity`).
2. **Phase 17: Architectural Split.** Begin decoupling the frontend UI (for live agent thinking streams) from the backend, moving towards the React + Tauri "Thick Client" architecture discussed, ensuring tools run on the user's desktop.

## 23. How to work with the human developer

For every milestone:
- explain the classes touched, the runtime flow, and why the abstraction exists.
- explain how to manually test it.
- explicitly state what MARK can and cannot do after the milestone.
- Do not silently make large architectural changes.

## 24. Current objective

Continue from the existing Git state. Do not replace the architecture. Propose the smallest next change needed to implement the next milestone.
