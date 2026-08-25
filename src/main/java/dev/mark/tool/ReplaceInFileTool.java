package dev.mark.tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Replaces text or regex patterns in a specific file.
 */
@Component
public class ReplaceInFileTool implements Tool {
    private final Path basePath;

    public ReplaceInFileTool(FileSystemProperties properties) {
        this.basePath = Path.of(properties.basePath()).toAbsolutePath();
    }

    @Override public String name() { return "replace_in_file"; }

    @Override public String description() {
        return "Find and replace text or regex patterns inside a specific file.";
    }

    @Override public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of(
                                "type", "string",
                                "description", "Relative path of the file to edit."),
                        "target", Map.of(
                                "type", "string",
                                "description", "The string or regex pattern to search for and replace."),
                        "replacement", Map.of(
                                "type", "string",
                                "description", "The new string to replace the target with."),
                        "isRegex", Map.of(
                                "type", "boolean",
                                "description", "True if the target is a regular expression, false for literal string matching.")
                ),
                "required", List.of("path", "target", "replacement", "isRegex"));
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        Object rawPath = request.arguments().get("path");
        Object rawTarget = request.arguments().get("target");
        Object rawReplacement = request.arguments().get("replacement");
        Object rawIsRegex = request.arguments().get("isRegex");

        if (!(rawPath instanceof String pathStr) || pathStr.isBlank()) {
            return new ToolResult(false, "replace_in_file requires a non-blank path argument", Map.of());
        }
        if (!(rawTarget instanceof String targetStr) || targetStr.isEmpty()) {
            return new ToolResult(false, "replace_in_file requires a non-empty target argument", Map.of());
        }
        if (!(rawReplacement instanceof String replacementStr)) {
            return new ToolResult(false, "replace_in_file requires a replacement argument", Map.of());
        }
        if (!(rawIsRegex instanceof Boolean isRegex)) {
            return new ToolResult(false, "replace_in_file requires a boolean isRegex argument", Map.of());
        }

        try {
            Path file = FileSystemUtils.resolveSandboxed(basePath, pathStr);
            if (!Files.exists(file) || !Files.isRegularFile(file)) {
                return new ToolResult(false, "File does not exist or is not a regular file: " + pathStr, Map.of());
            }

            String content = Files.readString(file, StandardCharsets.UTF_8);
            String newContent;

            if (isRegex) {
                newContent = content.replaceAll(targetStr, replacementStr);
            } else {
                newContent = content.replace(targetStr, replacementStr);
            }

            if (content.equals(newContent)) {
                return new ToolResult(true, "No changes made. The target pattern was not found in the file.", Map.of("changed", false));
            }

            Files.writeString(file, newContent, StandardCharsets.UTF_8);
            
            return new ToolResult(true, "Successfully replaced text in " + pathStr, Map.of("changed", true));

        } catch (IllegalArgumentException e) {
            return new ToolResult(false, e.getMessage(), Map.of());
        } catch (IOException e) {
            return new ToolResult(false, "Failed to edit file: " + e.getMessage(), Map.of());
        }
    }
}
