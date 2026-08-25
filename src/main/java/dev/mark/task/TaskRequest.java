package dev.mark.task;

import jakarta.validation.constraints.NotBlank;

public record TaskRequest(@NotBlank(message = "goal is required") String goal, String provider) { }
