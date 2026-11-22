# Module ownership

## User

Owns:
- Account identity and profile.
- Locale and time zone.
- UI preferences.
- Default execution mode.
- Default approval preferences.
- Preferred model-profile ID.
- Preferred device ID.
- User-configurable agent defaults (for example, a maximum step limit).

Does not own:
- Model provider credentials or inference logic.
- Device connection state.
- Task history.

## Task

Owns:
- Task ID, goal, title, creation/update timestamps.
- Authoritative task status.
- Chronological activity events.
- Final response and output/artifact references.
- The selected device/model IDs.
- A snapshot of effective preferences used when the task started.

Does not own:
- Planning or tool selection.
- Model invocation logic.
- Windows execution.

## Agent

Owns:
- Agent orchestration loop.
- Prompt/context construction.
- Planning and model interaction decisions.
- L0 deterministic skill matching.
- Tool catalogue and tool-call coordination.
- Verification, retry, and recovery decisions.
- Approval requests.

Does not own:
- Persisted user profile.
- The authoritative task record.
- Device pairing or the Windows connection.

## Model

Owns:
- Provider adapters (Ollama, llama.cpp, and optional hosted providers).
- Model catalogue and model-profile metadata.
- Capability metadata (text, tools, vision).
- Inference request/response mapping.
- Model routing and inference timeouts.

User preferences reference a model profile. Provider secrets are held in protected secret storage, not ordinary settings or prompts.

## Cloud Device module

Owns:
- Device pairing and revocation.
- Device ID, owner, display name, OS, runtime version.
- Advertised capabilities.
- Online/offline state and last heartbeat.
- Active authenticated connection.
- Command dispatch and command correlation.

It does not perform mouse, keyboard, browser, or filesystem operations.

## Local Device Runtime

Owns:
- Local device identity and pairing credential.
- Outbound secure connection.
- Command validation and local permission checks.
- Browser, window, screenshot, mouse, keyboard, filesystem, and system adapters.
- Execution evidence and command result reporting.

## Memory

Deferred. Do not create vector databases, RAG pipelines, embeddings, or memory abstractions until concrete workflows define what should be remembered, why, for how long, and how users can inspect/delete it.
