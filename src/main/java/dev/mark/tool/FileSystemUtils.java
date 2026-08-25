package dev.mark.tool;

import java.nio.file.Path;

/**
 * Shared utility for filesystem tools.
 *
 * <p>Resolves a user-supplied relative path against the sandbox base directory
 * and verifies the result stays within the sandbox.  This prevents path-traversal
 * attacks from untrusted LLM-generated input (e.g. {@code "../../etc/passwd"}).
 */
final class FileSystemUtils {
    private FileSystemUtils() { }

    /**
     * Resolve {@code relativePath} against {@code basePath} and ensure the
     * result is still under {@code basePath}.
     *
     * @throws IllegalArgumentException if the resolved path escapes the sandbox
     */
    static Path resolveSandboxed(Path basePath, String relativePath) {
        Path normalized = basePath.normalize();
        Path resolved = normalized.resolve(relativePath).normalize();
        if (!resolved.startsWith(normalized)) {
            throw new IllegalArgumentException("Path escapes sandbox: " + relativePath);
        }
        return resolved;
    }
}
