package dev.mark.vision.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Robust bounding box representation with spatial geometry, containment, and IoU calculations.
 */
public record BoundingBox(
        @JsonProperty("x") int x,
        @JsonProperty("y") int y,
        @JsonProperty("width") int width,
        @JsonProperty("height") int height
) {
    public BoundingBox {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(String.format("Invalid dimensions: width=%d, height=%d (must be > 0)", width, height));
        }
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException(String.format("Invalid origin: x=%d, y=%d (must be >= 0)", x, y));
        }
    }

    @JsonIgnore
    public int xMax() {
        return x + width;
    }

    @JsonIgnore
    public int yMax() {
        return y + height;
    }

    @JsonIgnore
    public long area() {
        return (long) width * height;
    }

    @JsonIgnore
    public ScreenPoint center() {
        return new ScreenPoint(x + width / 2, y + height / 2);
    }

    public boolean contains(int px, int py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    public boolean contains(ScreenPoint pt) {
        return pt != null && contains(pt.x(), pt.y());
    }

    public boolean intersects(BoundingBox other) {
        if (other == null) return false;
        return !(other.x >= this.x + this.width ||
                other.x + other.width <= this.x ||
                other.y >= this.y + this.height ||
                other.y + other.height <= this.y);
    }

    public BoundingBox intersection(BoundingBox other) {
        if (!intersects(other)) return null;
        int ix1 = Math.max(this.x, other.x);
        int iy1 = Math.max(this.y, other.y);
        int ix2 = Math.min(this.x + this.width, other.x + other.width);
        int iy2 = Math.min(this.y + this.height, other.y + other.height);
        return new BoundingBox(ix1, iy1, ix2 - ix1, iy2 - iy1);
    }

    public double intersectionOverUnion(BoundingBox other) {
        if (other == null || !intersects(other)) return 0.0;
        BoundingBox inter = intersection(other);
        if (inter == null) return 0.0;
        long interArea = inter.area();
        long unionArea = this.area() + other.area() - interArea;
        if (unionArea <= 0) return 0.0;
        return (double) interArea / unionArea;
    }
}
