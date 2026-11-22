# MARK Desktop

Tauri v2 wrapper for the React frontend and local Device Runtime.

This folder is intentionally only a placeholder in the first milestone. Tauri packaging and sidecar lifecycle management will be added after the cloud API and Device Runtime pairing flow are working independently.

The desktop application will:
- display the React UI,
- authenticate with the cloud backend,
- start/stop the local Device Runtime sidecar,
- show the runtime connection state.

It will not implement agent orchestration or duplicate backend business logic.
