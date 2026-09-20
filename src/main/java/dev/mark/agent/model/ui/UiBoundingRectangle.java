package dev.mark.agent.model.ui;

public record UiBoundingRectangle(int x, int y, int width, int height) {
    public boolean isValid() {
        return width > 0 && height > 0;
    }
}
