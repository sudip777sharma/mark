# MARK agent notes

MARK is a local-first autonomous-agent prototype. It uses Java 21, Spring Boot 3.5, Maven Wrapper, JUnit, Playwright, and an H2 development database. It has an LLM-driven task loop plus file, browser, desktop, and system tools; treat these tools as potentially consequential.

Build with `./mvnw clean verify`; run tests with `./mvnw test`; start with `./mvnw spring-boot:run`.

Keep the codebase small. Prefer deterministic tools and structured interfaces. Preserve the narrow tool and LLM-provider abstractions so providers and tools can be added without changing orchestration. Important actions must retain explicit observation and verification paths. Do not add a major framework or capability without a separately agreed milestone.

## Agent Interaction Rules
For any issue or concern raised by the user, the agent MUST follow these three steps BEFORE making any changes:
1. **Confirm the concern**: Acknowledge and restate the user's concern to ensure it is fully understood.
2. **Discuss the root cause**: Analyze and explain what is causing the issue.
3. **Discuss the resolution**: Present the research and analysis for how the concern will be resolved, and ask for confirmation before proceeding with any code edits.
