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
import java.util.Map;

/**
 * Write content to a file in the sandboxed workspace.
 * Implements strict contract validation, parent directory preparation, and verified readback.
 */
@Component
public class WriteFileTool implements ContractAwareTool {
    private final Path basePath;

    public WriteFileTool(dev.mark.preference.UserPreferenceService userPreferenceService, FileSystemPropertiesConfig properties) {
        this.basePath = Path.of(userPreferenceService.getString("filesystem.basePath", properties.basePath())).toAbsolutePath();
    }

    @Override public String name() { return "write_file"; }

    @Override public String description() {
        return "Write content to a file. Path is relative to the workspace root. Creates parent directories if needed.";
    }

    @Override
    public ToolContract contract() {
        return ToolContract.builder(name())
                .description(description())
                .category("FILESYSTEM")
                .risk(ToolRiskClassification.WORKSPACE_MUTATION)
                .idempotency(IdempotencyPolicy.NATURALLY_IDEMPOTENT)
                .timeoutMs(10_000L)
                .parameter(ToolParameterSchema.requiredString("path", "Relative path of the file to write"))
                .parameter(ToolParameterSchema.requiredString("content", "Text content to write to the file"))
                .precondition(req -> {
                    String path = (String) req.arguments().get("path");
                    if (path == null || path.isBlank()) return PreconditionCheck.PreconditionResult.fail("Path cannot be blank");
                    try {
                        Path resolved = FileSystemUtils.resolveSandboxed(basePath, path);
                        if (Files.isDirectory(resolved)) return PreconditionCheck.PreconditionResult.fail("Target path is an existing directory: " + path);
                        return PreconditionCheck.PreconditionResult.pass();
                    } catch (Exception ex) {
                        return PreconditionCheck.PreconditionResult.fail("Invalid path: " + ex.getMessage());
                    }
                })
                .postcondition((req, res) -> {
                    if (!res.successful()) return PostconditionCheck.PostconditionResult.fail("Write failed", Map.of());
                    Boolean verified = (Boolean) res.metadata().get("verified");
                    if (Boolean.TRUE.equals(verified)) {
                        return PostconditionCheck.PostconditionResult.pass("Written content verified on disk", res.metadata());
                    }
                    return PostconditionCheck.PostconditionResult.fail("Written content did not match readback verification", res.metadata());
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
            return new ToolResultDTO(false, "write_file requires a non-blank string path argument", Map.of());
        }
        Object rawContent = request.arguments().get("content");
        if (!(rawContent instanceof String content)) {
            return new ToolResultDTO(false, "write_file requires a string content argument", Map.of());
        }
        try {
            Path resolved = FileSystemUtils.resolveSandboxed(basePath, pathStr);
            Path parent = resolved.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.writeString(resolved, content);
            long bytesWritten = content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            boolean verified = Files.isRegularFile(resolved) && content.equals(Files.readString(resolved));
            return new ToolResultDTO(verified, verified ? "Written " + bytesWritten + " bytes to " + pathStr : "Failed to verify file write on disk",
                    Map.of("path", pathStr, "bytesWritten", bytesWritten, "verified", verified));
        } catch (IllegalArgumentException e) {
            return new ToolResultDTO(false, e.getMessage(), Map.of());
        } catch (IOException e) {
            return new ToolResultDTO(false, "Failed to write file: " + e.getMessage(), Map.of());
        }
    }
}
