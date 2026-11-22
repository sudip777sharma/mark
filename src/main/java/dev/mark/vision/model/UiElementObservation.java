package dev.mark.vision.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Strongly-typed observation of an individual UI element on screen.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UiElementObservation(
        @JsonProperty("element_id") String elementId,
        @JsonProperty("source") ElementSource source,
        @JsonProperty("type") String type,
        @JsonProperty("text") String text,
        @JsonProperty("bounding_box") BoundingBox boundingBox,
        @JsonProperty("center") ScreenPoint center,
        @JsonProperty("confidence") double confidence,
        @JsonProperty("visible") boolean visible,
        @JsonProperty("enabled") boolean enabled,
        @JsonProperty("actionable") boolean actionable,
        @JsonProperty("automation_id") String automationId,
        @JsonProperty("control_pattern") String controlPattern
) {
    public UiElementObservation {
        if (elementId == null || elementId.isBlank()) {
            throw new IllegalArgumentException("elementId cannot be null or empty");
        }
        if (source == null) {
            throw new IllegalArgumentException("source cannot be null");
        }
        if (boundingBox == null) {
            throw new IllegalArgumentException("boundingBox cannot be null");
        }
        if (center == null) {
            center = boundingBox.center();
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Confidence must be in range [0.0, 1.0]: " + confidence);
        }
    }

    public static Builder builder(String elementId, ElementSource source, BoundingBox boundingBox) {
        return new Builder(elementId, source, boundingBox);
    }

    public static class Builder {
        private final String elementId;
        private final ElementSource source;
        private final BoundingBox boundingBox;
        private String type = "element";
        private String text;
        private ScreenPoint center;
        private double confidence = 1.0;
        private boolean visible = true;
        private boolean enabled = true;
        private boolean actionable = true;
        private String automationId;
        private String controlPattern;

        public Builder(String elementId, ElementSource source, BoundingBox boundingBox) {
            this.elementId = elementId;
            this.source = source;
            this.boundingBox = boundingBox;
            this.center = boundingBox != null ? boundingBox.center() : null;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder center(ScreenPoint center) {
            this.center = center;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder actionable(boolean actionable) {
            this.actionable = actionable;
            return this;
        }

        public Builder automationId(String automationId) {
            this.automationId = automationId;
            return this;
        }

        public Builder controlPattern(String controlPattern) {
            this.controlPattern = controlPattern;
            return this;
        }

        public UiElementObservation build() {
            return new UiElementObservation(
                    elementId, source, type, text, boundingBox, center,
                    confidence, visible, enabled, actionable, automationId, controlPattern
            );
        }
    }
}
