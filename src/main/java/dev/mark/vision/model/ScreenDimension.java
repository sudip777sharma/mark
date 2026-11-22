package dev.mark.vision.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Encapsulates the physical and virtual display dimensions and scaling factor.
 */
public record ScreenDimension(
        @JsonProperty("width") int width,
        @JsonProperty("height") int height,
        @JsonProperty("scale_factor") double scaleFactor
) {
    public ScreenDimension {
        if (width <= 0) {
            throw new IllegalArgumentException("Screen width must be positive: " + width);
        }
        if (height <= 0) {
            throw new IllegalArgumentException("Screen height must be positive: " + height);
        }
        if (scaleFactor <= 0.0) {
            throw new IllegalArgumentException("Screen scale factor must be positive: " + scaleFactor);
        }
    }
}
