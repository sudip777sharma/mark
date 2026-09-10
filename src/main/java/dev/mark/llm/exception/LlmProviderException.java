package dev.mark.llm.exception;

/**
 * - Custom runtime exception for LLM provider interaction errors.
 * - Standardizes failures from third-party AI services.
 * - Simplifies error handling by encapsulating external API errors.
 * - Sits between the external API client and the application's internal error handling.
 * - Provides constructors to capture custom error messages and the original cause.
 * - Intercepts external API failures and propagates them to global exception handlers for consistent response management.
 */
public class LlmProviderException extends RuntimeException {

    public LlmProviderException(String message) {
        super(message);
    }

    public LlmProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
