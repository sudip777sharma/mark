package dev.mark.tool.impl.file;

import dev.mark.tool.config.FileSystemPropertiesConfig;
import dev.mark.tool.contract.*;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import dev.mark.tool.util.FileSystemUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Deletes a file or directory in the sandboxed workspace.
 * Implements strict contract validation, root deletion prevention, and verified postconditions.
 */
@Component
public class DeleteFileTool implements ContractAwareTool {
    private final Path basePath;

    public DeleteFileTool(FileSystemPropertiesConfig properties) {
        this.basePath = Path.of(properties.basePath()).toAbsolutePath();
    }

    @Override public String name() { return "delete_file"; }

    @Override public String description() {
        return "Delete a file or directory. Path is relative to the workspace root. If a directory is specified, it will be deleted recursively.";
    }

    @Override
    public ToolContract contract() {
        return ToolContract.builder(name())
                .description(description())
                .category("FILESYSTEM")
                .risk(ToolRiskClassification.HIGH_RISK_DESTRUCTIVE)
                .idempotency(IdempotencyPolicy.NATURALLY_IDEMPOTENT)
                .timeoutMs(15_000L)
                .parameter(ToolParameterSchema.requiredString("path", "Relative path of the file or directory to delete"))
                .precondition(req -> {
                    String path = (String) req.arguments().get("path");
                    if (path == null || path.isBlank()) return PreconditionCheck.PreconditionResult.fail("Path cannot be blank");
                    String norm = path.trim().replace('\\', '/');
                    if (norm.equals(".") || norm.equals("./") || norm.equals("/")) {
                        return PreconditionCheck.PreconditionResult.fail("Deleting the workspace root directory is blocked by security policy.");
                    }
                    try {
                        Path resolved = FileSystemUtils.resolveSandboxed(basePath, path);
                        if (!Files.exists(resolved)) {
                            return PreconditionCheck.PreconditionResult.fail("Path does not exist: " + path);
                        }
                        return PreconditionCheck.PreconditionResult.pass();
                    } catch (Exception ex) {
                        return PreconditionCheck.PreconditionResult.fail("Invalid path: " + ex.getMessage());
                    }
                })
                .postcondition((req, res) -> {
                    if (!res.successful()) return PostconditionCheck.PostconditionResult.fail("Deletion failed", Map.of());
                    Boolean verified = (Boolean) res.metadata().get("verified");
                    if (Boolean.TRUE.equals(verified)) {
                        return PostconditionCheck.PostconditionResult.pass("Target absence verified on disk", res.metadata());
                    }
                    return PostconditionCheck.PostconditionResult.fail("Target still exists after delete attempt", res.metadata());
                })
                .build();
    }

    @Override public Map<String, Object> parameterSchema() {
        return contract().toJsonSchema();
    }

    @Override
    public ToolResultDTO execute(ToolRequestDTO request) {
        Object rawPath = request.arguments().get("path");
        if (!(rawPath instanceof String pathStr) || pathStr.isBlank()) {
            return new ToolResultDTO(false, "delete_file requires a non-blank string path argument", Map.of());
        }

        String norm = pathStr.trim().replace('\\', '/');
        if (norm.equals(".") || norm.equals("./") || norm.equals("/")) {
            return new ToolResultDTO(false, "Deleting the workspace root directory is blocked by security policy.", Map.of());
        }

        try {
            Path resolved = FileSystemUtils.resolveSandboxed(basePath, pathStr);
            if (!Files.exists(resolved)) {
                return new ToolResultDTO(false, "Path does not exist: " + pathStr, Map.of());
            }

            if (Files.isDirectory(resolved)) {
                try (Stream<Path> walk = Files.walk(resolved)) {
                    walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(java.io.File::delete);
                }
            } else {
                Files.delete(resolved);
            }

            boolean verified = !Files.exists(resolved);
            return new ToolResultDTO(verified, verified ? "Successfully deleted " + pathStr : "Failed to verify deletion of " + pathStr,
                    Map.of("path", pathStr, "verified", verified));
        } catch (IllegalArgumentException e) {
            return new ToolResultDTO(false, e.getMessage(), Map.of());
        } catch (IOException e) {
            return new ToolResultDTO(false, "Failed to delete path: " + e.getMessage(), Map.of());
        }
    }
}
