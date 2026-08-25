# MARK agent notes

MARK is a local-first autonomous-agent foundation. It uses Java 21, Spring Boot, Maven, and JUnit. This first milestone supplies only the agent-loop skeleton; no real model, browser, file, shell, cloud, or database integration exists yet.

Build with `mvn clean verify`; run tests with `mvn test`; start with `mvn spring-boot:run`.

Keep the codebase small. Prefer deterministic tools and structured interfaces. Preserve the narrow `Tool` and `LlmProvider` abstractions so providers and tools can be added without changing the engine. Do not add agent frameworks, provider SDKs, browser automation, or future-milestone features early. Important actions must retain explicit observation and verification paths.
