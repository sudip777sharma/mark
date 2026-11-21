package dev.mark.tool.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared utility for secure filesystem operations and sandbox enforcement.
 * Prevents path traversal and symbolic link escape vulnerabilities.
 */
public final class FileSystemUtils {
    private FileSystemUtils() { }

    /**
     * Resolve {@code relativePath} against {@code basePath} and ensure the
     * result is strictly contained within {@code basePath}.
     * Also checks real paths to prevent symlink traversal attacks.
     *
     * @throws IllegalArgumentException if the resolved path escapes the sandbox
     */
    public static Path resolveSandboxed(Path basePath, String relativePath) {
        if (relativePath == null) {
            throw new IllegalArgumentException("Path cannot be null");
        }
        Path normalizedBase = basePath.toAbsolutePath().normalize();
        Path resolved = normalizedBase.resolve(relativePath).normalize();

        if (!resolved.startsWith(normalizedBase)) {
            throw new IllegalArgumentException("Path escapes sandbox: " + relativePath);
        }

        // Symlink protection: if the target or its parent exists, verify real path
        if (Files.exists(resolved)) {
            try {
                Path realPath = resolved.toRealPath();
                if (!realPath.startsWith(normalizedBase)) {
                    throw new IllegalArgumentException("Symbolic link target escapes sandbox: " + relativePath);
                }
            } catch (IOException ignored) {
                // If realPath cannot be resolved due to permissions, fall back to safe normalized check
            }
        } else {
            // Check existing parent directory
            Path parent = resolved.getParent();
            while (parent != null && !Files.exists(parent)) {
                parent = parent.getParent();
            }
            if (parent != null && Files.exists(parent)) {
                try {
                    Path realParent = parent.toRealPath();
                    if (!realParent.startsWith(normalizedBase)) {
                        throw new IllegalArgumentException("Parent symbolic link escapes sandbox: " + relativePath);
                    }
                } catch (IOException ignored) {}
            }
        }

        return resolved;
    }
}
