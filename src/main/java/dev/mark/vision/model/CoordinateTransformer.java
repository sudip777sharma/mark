package dev.mark.vision.model;

/**
 * Coordinate transformation engine between VLM normalized relative coordinates [0, 1000]
 * and actual desktop pixel coordinates, accounting for display boundaries and scaling.
 */
public class CoordinateTransformer {

    private final ScreenDimension screen;

    public CoordinateTransformer(ScreenDimension screen) {
        if (screen == null) {
            throw new IllegalArgumentException("ScreenDimension cannot be null");
        }
        this.screen = screen;
    }

    public ScreenDimension screen() {
        return screen;
    }

    /**
     * Converts normalized VLM point [0, 1000] to actual desktop screen pixel coordinates.
     */
    public ScreenPoint denormalizePoint(int normX, int normY) {
        int clampedNormX = Math.clamp(normX, 0, 1000);
        int clampedNormY = Math.clamp(normY, 0, 1000);

        int px = (int) Math.round(((double) clampedNormX / 1000.0) * screen.width());
        int py = (int) Math.round(((double) clampedNormY / 1000.0) * screen.height());

        // Clamp to screen boundaries
        px = Math.clamp(px, 0, Math.max(0, screen.width() - 1));
        py = Math.clamp(py, 0, Math.max(0, screen.height() - 1));

        return new ScreenPoint(px, py);
    }

    /**
     * Converts normalized VLM bounding box [ymin, xmin, ymax, xmax] (scaled to 1000)
     * to desktop screen BoundingBox in pixels.
     */
    public BoundingBox denormalizeBox(int normYmin, int normXmin, int normYmax, int normXmax) {
        int clampedXmin = Math.clamp(normXmin, 0, 1000);
        int clampedYmin = Math.clamp(normYmin, 0, 1000);
        int clampedXmax = Math.clamp(normXmax, clampedXmin + 1, 1000);
        int clampedYmax = Math.clamp(normYmax, clampedYmin + 1, 1000);

        int px = (int) Math.round(((double) clampedXmin / 1000.0) * screen.width());
        int py = (int) Math.round(((double) clampedYmin / 1000.0) * screen.height());
        int pxMax = (int) Math.round(((double) clampedXmax / 1000.0) * screen.width());
        int pyMax = (int) Math.round(((double) clampedYmax / 1000.0) * screen.height());

        int w = Math.max(1, pxMax - px);
        int h = Math.max(1, pyMax - py);

        // Assert screen boundaries
        if (px + w > screen.width()) {
            w = Math.max(1, screen.width() - px);
        }
        if (py + h > screen.height()) {
            h = Math.max(1, screen.height() - py);
        }

        return new BoundingBox(px, py, w, h);
    }

    /**
     * Normalizes desktop pixel coordinates to relative [0, 1000] scale.
     */
    public ScreenPoint normalizePoint(int px, int py) {
        int nx = (int) Math.round(((double) Math.clamp(px, 0, screen.width()) / screen.width()) * 1000.0);
        int ny = (int) Math.round(((double) Math.clamp(py, 0, screen.height()) / screen.height()) * 1000.0);
        return new ScreenPoint(Math.clamp(nx, 0, 1000), Math.clamp(ny, 0, 1000));
    }

    /**
     * Normalizes desktop BoundingBox to [0, 1000] relative scale.
     */
    public BoundingBox normalizeBox(BoundingBox box) {
        if (box == null) return null;
        int nx = (int) Math.round(((double) box.x() / screen.width()) * 1000.0);
        int ny = (int) Math.round(((double) box.y() / screen.height()) * 1000.0);
        int nw = Math.max(1, (int) Math.round(((double) box.width() / screen.width()) * 1000.0));
        int nh = Math.max(1, (int) Math.round(((double) box.height() / screen.height()) * 1000.0));
        return new BoundingBox(Math.clamp(nx, 0, 1000), Math.clamp(ny, 0, 1000), nw, nh);
    }
}
