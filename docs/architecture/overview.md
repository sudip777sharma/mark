# MARK architecture

## Deployment shape

MARK is not a microservice system.

It has:
1. One cloud Spring Boot application (modular monolith).
2. One local Windows Device Runtime process.
3. One React desktop UI, packaged with Tauri in a later milestone.

The cloud backend can be deployed independently. The local runtime is installed on each Windows machine that MARK is allowed to operate.

## Backend modules

| Module | Owns |
|---|---|
| User | User profile, preferences, UI defaults, execution defaults |
| Task | Task records, authoritative lifecycle, activity feed, final result |
| Agent | Orchestration, planning, prompt construction, L0 skills, tool coordination, verification, recovery |
| Model | Model catalogue, provider adapters, model profiles, inference routing |
| Device | Pairing, device registry, connection status, capabilities, command dispatch |
| Memory | Deferred; no implementation until its use cases and retention rules are agreed |

These are code modules, not separately deployed services.

## Responsibility boundaries

- User stores the preferred model-profile ID; Model owns the actual model profile and provider implementation.
- User stores a preferred device ID; Device owns paired-device metadata and connection status.
- Task stores the effective settings snapshot and device/model references used for that run.
- Task is the source of truth for user-visible task status and activity history.
- Agent decides what to do and asks Model for inference. Agent does not own user profiles or device connections.
- The cloud Device module routes commands to a paired runtime. It does not execute Windows operations itself.
- Device Runtime validates and executes commands locally, then reports evidence.

## Task lifecycle

Initial statuses:

- QUEUED
- PLANNING
- EXECUTING
- WAITING_FOR_APPROVAL
- WAITING_FOR_DEVICE
- VERIFYING
- COMPLETED
- FAILED
- CANCELLED

A task's activity feed is chronological. It records concise status messages, tool start/result events, approval requests, errors, and completion. It is not a separate trace product.

## Device connection

Device Runtime initiates an authenticated outbound WSS connection to the cloud. The cloud does not open an inbound connection to the user's PC. The runtime sends a heartbeat and receives typed, expiring command envelopes.

The runtime runs in the signed-in Windows user's interactive session, not as a Windows Service, because desktop interaction requires access to that session.

## Configuration flow

The UI loads a small bootstrap response for the user's preferences, available model choices, and paired-device summaries. When a task is submitted, Task stores an effective configuration snapshot so later preference changes do not silently alter a running task.

Secrets are not returned to the UI or inserted into prompts. Provider credentials belong in protected secret storage.
