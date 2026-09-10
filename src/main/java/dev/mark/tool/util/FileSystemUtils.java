package dev.mark.tool.util;

import java.nio.file.Path;

/**
 * - What the class does: Provides shared filesystem utility operations, specifically secure path resolution.
 * - Why it is useful: Prevents path-traversal security vulnerabilities by ensuring untrusted inputs stay contained.
 * - How it fits in the flow of the application: Acts as a security gatekeeper whenever user or LLM-supplied paths need to be accessed on disk.
 * - Its methods and variables and how they are useful: Contains the private constructor to prevent instantiation and the resolveSandboxed method to safely validate paths.
 * - Its logic and how it gets fit into the overall application logic: Normalizes base and relative paths, resolves them together, and throws an exception if the result escapes the designated sandbox boundary.
 */

/**
 * Shared utility for filesystem tools.
 *
 * <p>Resolves a user-supplied relative path against the sandbox base directory
 * and verifies the result stays within the sandbox.  This prevents path-traversal
 * attacks from untrusted LLM-generated input (e.g. {@code "../../etc/passwd"}).
 */
public final class FileSystemUtils {
    private FileSystemUtils() { }

    /**
     * Resolve {@code relativePath} against {@code basePath} and ensure the
     * result is still under {@code basePath}.
     *
     * @throws IllegalArgumentException if the resolved path escapes the sandbox
     */
    public static Path resolveSandboxed(Path basePath, String relativePath) {
        Path normalized = basePath.normalize();
        Path resolved = normalized.resolve(relativePath).normalize();
        if (!resolved.startsWith(normalized)) {
            throw new IllegalArgumentException("Path escapes sandbox: " + relativePath);
        }
        return resolved;
    }
}
