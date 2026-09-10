package dev.mark.agent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Inbound payload representing any user interaction (voice transcript or typed prompt).
 */
public record InteractionRequestDTO(
    @NotBlank(message = "User input must not be blank")
    String input
) {}
