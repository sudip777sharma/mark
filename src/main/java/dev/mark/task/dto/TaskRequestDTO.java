package dev.mark.task.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * - Represents the Data Transfer Object for incoming task creation and update requests.
 * - Ensures incoming payload integrity by enforcing mandatory field validations.
 * - Acts as the entry point for client data at the controller layer before service processing.
 * - Contains the goal field validated with NotBlank and an optional provider field to configure task execution.
 * - Integrates into the request validation flow by automatically triggering Jakarta constraints prior to business logic execution.
 */
public record TaskRequestDTO(
        @NotBlank(message = "Goal cannot be blank")
        String goal,
        String provider
) {
}
