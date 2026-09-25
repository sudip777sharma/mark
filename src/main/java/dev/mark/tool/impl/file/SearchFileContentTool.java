package dev.mark.tool.impl.file;


import dev.mark.tool.util.FileSystemUtils;
import dev.mark.tool.config.FileSystemPropertiesConfig;
import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * **1. What the class does:**
 * Searches for specific text content across files in a workspace using either literal strings or regular expressions (similar to grep).
 *
 * **2. Why it is useful:**
 * It allows the application or an LLM agent to quickly locate code patterns, variables, or configurations across multiple files within a sandboxed directory without reading each file individually.
 *
 * **3. How it fits in the flow of the application:**
 * It is a Spring-managed `@Component` implementing the `Tool` interface. It is dynamically discovered and executed by the application's tool orchestrator when a file search capability is requested.
 *
 * **4. Methods and variables:**
 * - `basePath`: A `Path` variable representing the secure, sandboxed root directory.
 * - `name()`: Returns the unique identifier "search_file_content".
 * - `description()`: Provides the tool's functional description for LLM consumption.
 * - `parameterSchema()`: Defines the required input parameters (directory, query, isRegex).
 * - `execute(ToolRequestDTO)`: Validates inputs, performs the search, and returns the matches.
 *
 * **5. Logic and integration:**
 * - Resolves the target directory safely using `FileSystemUtils.resolveSandboxed` to prevent path traversal.
 * - Compiles the query into a `Pattern` (using `Pattern.quote` if `isRegex` is false).
 * - Walks the directory tree, reads files line-by-line, matches lines against the pattern, and collects up to 50 results.
 * - Returns a `ToolResultDTO` containing formatted matches (file path, line number, and content) or error details back to the calling orchestrator.
 */

/**
 * Searches for content across files in the workspace using literal strings or regex.
 */
@Component
public class SearchFileContentTool implements Tool {
    private final Path basePath;

    public SearchFileContentTool(dev.mark.preference.UserPreferenceService userPreferenceService, FileSystemPropertiesConfig properties) {
        this.basePath = Path.of(userPreferenceService.getString("filesystem.basePath", properties.basePath())).toAbsolutePath();
    }

    @Override public String name() { return "search_file_content"; }

    @Override public String description() {
        return "Search file contents in the workspace using a literal string or regular expression. Equivalent to grep. Returns matched lines with line numbers.";
    }

    @Override public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "directory", Map.of(
                                "type", "string",
                                "description", "Relative path of the directory to search in, or '.' for the whole workspace."),
                        "query", Map.of(
                                "type", "string",
                                "description", "The string or regex pattern to search for."),
                        "isRegex", Map.of(
                                "type", "boolean",
                                "description", "True if the query is a regular expression, false for literal string matching.")
                ),
                "required", List.of("directory", "query", "isRegex"));
    }

    @Override
    public ToolResultDTO execute(ToolRequestDTO request) {
        Object rawDir = request.arguments().get("directory");
        Object rawQuery = request.arguments().get("query");
        Object rawIsRegex = request.arguments().get("isRegex");

        if (!(rawDir instanceof String dirStr) || dirStr.isBlank()) {
            return new ToolResultDTO(false, "search_file_content requires a non-blank directory argument", Map.of());
        }
        if (!(rawQuery instanceof String queryStr) || queryStr.isEmpty()) {
            return new ToolResultDTO(false, "search_file_content requires a non-empty query argument", Map.of());
        }
        if (!(rawIsRegex instanceof Boolean isRegex)) {
            return new ToolResultDTO(false, "search_file_content requires a boolean isRegex argument", Map.of());
        }

        try {
            Path searchRoot = FileSystemUtils.resolveSandboxed(basePath, dirStr);
            if (!Files.exists(searchRoot)) {
                return new ToolResultDTO(false, "Directory does not exist: " + dirStr, Map.of());
            }

            Pattern pattern;
            if (isRegex) {
                pattern = Pattern.compile(queryStr);
            } else {
                pattern = Pattern.compile(Pattern.quote(queryStr));
            }

            List<String> results = new ArrayList<>();
            int maxResults = 50;

            try (Stream<Path> walk = Files.walk(searchRoot)) {
                List<Path> files = walk.filter(Files::isRegularFile).toList();

                for (Path file : files) {
                    if (results.size() >= maxResults) break;

                    try {
                        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                        for (int i = 0; i < lines.size(); i++) {
                            String line = lines.get(i);
                            Matcher matcher = pattern.matcher(line);
                            if (matcher.find()) {
                                Path relativePath = basePath.relativize(file);
                                results.add(relativePath.toString() + ":" + (i + 1) + ":" + line.trim());
                                if (results.size() >= maxResults) break;
                            }
                        }
                    } catch (Exception e) {
                        // Skip unreadable or binary files
                    }
                }
            }

            if (results.isEmpty()) {
                return new ToolResultDTO(true, "No matches found.", Map.of("matches", 0));
            }

            String output = String.join("\n", results);
            if (results.size() >= maxResults) {
                output += "\n\n(Results truncated to " + maxResults + " matches)";
            }

            return new ToolResultDTO(true, "Matches found:\n" + output, Map.of("matches", results.size()));

        } catch (IllegalArgumentException e) {
            return new ToolResultDTO(false, e.getMessage(), Map.of());
        } catch (IOException e) {
            return new ToolResultDTO(false, "Failed to search files: " + e.getMessage(), Map.of());
        }
    }
}
