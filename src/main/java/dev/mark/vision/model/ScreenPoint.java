package dev.mark.vision.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 2D point in integer desktop screen coordinates.
 */
public record ScreenPoint(
        @JsonProperty("x") int x,
        @JsonProperty("y") int y
) {
    public double distanceTo(ScreenPoint other) {
        if (other == null) return Double.MAX_VALUE;
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
