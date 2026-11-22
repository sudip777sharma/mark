package dev.mark.vision.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Top-level structured observation snapshot of the screen and detected elements.
 */
public record UiObservation(
        @JsonProperty("screenshot_id") String screenshotId,
        @JsonProperty("screen") ScreenDimension screen,
        @JsonProperty("elements") List<UiElementObservation> elements,
        @JsonProperty("uncertainties") List<String> uncertainties,
        @JsonProperty("observation_timestamp") String observationTimestamp
) {
    public UiObservation {
        if (screenshotId == null || screenshotId.isBlank()) {
            screenshotId = UUID.randomUUID().toString();
        }
        if (screen == null) {
            throw new IllegalArgumentException("screen dimension cannot be null");
        }
        if (elements == null) {
            elements = List.of();
        }
        if (uncertainties == null) {
            uncertainties = List.of();
        }
        if (observationTimestamp == null || observationTimestamp.isBlank()) {
            observationTimestamp = Instant.now().toString();
        }
    }
}
