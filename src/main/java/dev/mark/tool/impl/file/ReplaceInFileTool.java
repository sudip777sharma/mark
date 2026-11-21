package dev.mark.tool.impl.file;

import dev.mark.tool.config.FileSystemPropertiesConfig;
import dev.mark.tool.contract.*;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import dev.mark.tool.util.FileSystemUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Replaces text or regex patterns in a specific file.
 * Implements strict contract validation, precondition checking, and verified postconditions.
 */
@Component
public class ReplaceInFileTool implements ContractAwareTool {
    private final Path basePath;

    public ReplaceInFileTool(FileSystemPropertiesConfig properties) {
        this.basePath = Path.of(properties.basePath()).toAbsolutePath();
    }

    @Override public String name() { return "replace_in_file"; }

    @Override public String description() {
        return "Find and replace text or regex patterns inside a specific file within the workspace.";
    }

    @Override
    public ToolContract contract() {
        return ToolContract.builder(name())
                .description(description())
                .category("FILESYSTEM")
                .risk(ToolRiskClassification.WORKSPACE_MUTATION)
                .idempotency(IdempotencyPolicy.NON_IDEMPOTENT)
                .timeoutMs(10_000L)
                .parameter(ToolParameterSchema.requiredString("path", "Relative path of the file to edit."))
                .parameter(ToolParameterSchema.requiredString("target", "The string or regex pattern to search for and replace."))
                .parameter(ToolParameterSchema.requiredString("replacement", "The new string to replace the target with."))
                .parameter(ToolParameterSchema.optionalBoolean("isRegex", "True if target is regex, false for literal string matching.", false))
                .precondition(req -> {
                    String path = (String) req.arguments().get("path");
                    if (path == null || path.isBlank()) return PreconditionCheck.PreconditionResult.fail("Path cannot be blank");
                    try {
                        Path file = FileSystemUtils.resolveSandboxed(basePath, path);
                        if (!Files.exists(file)) return PreconditionCheck.PreconditionResult.fail("Target file does not exist: " + path);
                        if (!Files.isRegularFile(file)) return PreconditionCheck.PreconditionResult.fail("Target path is not a regular file: " + path);
                        return PreconditionCheck.PreconditionResult.pass();
                    } catch (Exception ex) {
                        return PreconditionCheck.PreconditionResult.fail("Invalid path: " + ex.getMessage());
                    }
                })
                .postcondition((req, res) -> {
                    if (!res.successful()) return PostconditionCheck.PostconditionResult.fail("Execution failed", Map.of());
                    Boolean verified = (Boolean) res.metadata().get("verified");
                    if (Boolean.TRUE.equals(verified)) {
                        return PostconditionCheck.PostconditionResult.pass("Replaced content strictly verified on disk", res.metadata());
                    }
                    return PostconditionCheck.PostconditionResult.fail("Post-replacement content verification failed on disk", res.metadata());
                })
                .build();
    }

    @Override public Map<String, Object> parameterSchema() {
        return contract().toJsonSchema();
    }

    @Override
    public ToolResultDTO execute(ToolRequestDTO request) {
        Object rawPath = request.arguments().get("path");
        Object rawTarget = request.arguments().get("target");
        Object rawReplacement = request.arguments().get("replacement");
        Object rawIsRegex = request.arguments().get("isRegex");

        if (!(rawPath instanceof String pathStr) || pathStr.isBlank()) {
            return new ToolResultDTO(false, "replace_in_file requires a non-blank path argument", Map.of());
        }
        if (!(rawTarget instanceof String targetStr) || targetStr.isEmpty()) {
            return new ToolResultDTO(false, "replace_in_file requires a non-empty target argument", Map.of());
        }
        if (!(rawReplacement instanceof String replacementStr)) {
            return new ToolResultDTO(false, "replace_in_file requires a replacement argument", Map.of());
        }
        boolean isRegex = Boolean.TRUE.equals(rawIsRegex);

        try {
            Path file = FileSystemUtils.resolveSandboxed(basePath, pathStr);
            if (!Files.exists(file) || !Files.isRegularFile(file)) {
                return new ToolResultDTO(false, "File does not exist or is not a regular file: " + pathStr, Map.of());
            }

            String content = Files.readString(file, StandardCharsets.UTF_8);
            String newContent;

            if (isRegex) {
                newContent = content.replaceAll(targetStr, replacementStr);
            } else {
                newContent = content.replace(targetStr, replacementStr);
            }

            if (content.equals(newContent)) {
                return new ToolResultDTO(false, "No changes made: the target pattern was not found in the file: " + pathStr,
                        Map.of("path", pathStr, "changed", false, "verified", false));
            }

            Files.writeString(file, newContent, StandardCharsets.UTF_8);
            boolean verified = Files.readString(file, StandardCharsets.UTF_8).equals(newContent);
            return new ToolResultDTO(verified, "Successfully replaced text in " + pathStr,
                    Map.of("path", pathStr, "changed", true, "verified", verified));

        } catch (IllegalArgumentException e) {
            return new ToolResultDTO(false, e.getMessage(), Map.of());
        } catch (IOException e) {
            return new ToolResultDTO(false, "Failed to edit file: " + e.getMessage(), Map.of());
        }
    }
}
