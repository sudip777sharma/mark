package dev.mark.vision.service;

import dev.mark.vision.model.ScreenDimension;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe in-memory cache for the latest screen captures to eliminate redundant disk I/O.
 */
@Service
public class ScreenshotCacheService {

    public record CachedScreenshot(
            String id,
            BufferedImage image,
            ScreenDimension dimension,
            String filePath,
            Instant timestamp
    ) {}

    private final AtomicReference<CachedScreenshot> latest = new AtomicReference<>();

    public void update(String id, BufferedImage image, ScreenDimension dimension, String filePath) {
        latest.set(new CachedScreenshot(id, image, dimension, filePath, Instant.now()));
    }

    public CachedScreenshot getLatest() {
        return latest.get();
    }

    public void clear() {
        latest.set(null);
    }
}
