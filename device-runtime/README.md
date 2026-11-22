# MARK Device Runtime

This is a separate Java application installed on a Windows PC that MARK is allowed to operate.

## Responsibility

- Pair with a MARK account.
- Open an authenticated outbound WSS connection to the cloud backend.
- Advertise device capabilities and heartbeat status.
- Receive typed, expiring command envelopes.
- Validate every command against local permissions.
- Execute approved browser, Windows, screenshot, input, filesystem, or system operations.
- Return results and evidence.

The runtime does not contain an LLM or planner. It does not accept arbitrary shell commands from the cloud.

## Windows session

Run as a normal application in the signed-in user's interactive session. Do not install desktop automation as a Windows Service; Windows Services run in Session 0 and cannot reliably operate the interactive desktop.

## Current state

Scaffold only. Pairing, WSS transport, command validation, and execution are intentionally not implemented in this first commit.
