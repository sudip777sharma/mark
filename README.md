# MARK

MARK is a local-first computer-use agent with a cloud-hosted brain and a local Windows execution runtime.

## Architecture

- **Cloud backend:** one Spring Boot modular monolith.
- **Windows device runtime:** a separate, lightweight Java process that connects outward to the cloud over authenticated WebSocket.
- **Desktop UI:** React + TypeScript + Vite + Tailwind CSS; Tauri will package the UI and local runtime later.

The backend is not split into microservices. User, Task, Agent, Model, and Device are modules with explicit boundaries inside one application. Memory is intentionally deferred.

## Repository layout

```text
backend/          Cloud Spring Boot application
device-runtime/   Local Windows execution client
frontend/         React desktop UI
desktop/          Tauri packaging (later milestone)
docs/             Architecture and decisions
```

## Prerequisites

- Java 21
- Maven 3.9+
- Node.js 22.12+
- npm

## Run the backend

```powershell
cd backend
mvn spring-boot:run
```

Health check: `http://localhost:8080/api/v1/health`

## Run the frontend

```powershell
cd frontend
npm install
npm run dev
```

Open the Vite URL shown in the terminal (normally `http://localhost:5173`).

The first frontend milestone uses clearly identified preview data. Task submission, authentication, device pairing, and cloud connectivity are not implemented yet.

## Engineering rule

Prefer the smallest implementation that satisfies a tested requirement. Do not add frameworks, abstractions, services, background workers, or configuration layers without a concrete need and an agreed milestone.
