package dev.mark.tool;

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
 * Searches for content across files in the workspace using literal strings or regex.
 */
@Component
public class SearchFileContentTool implements Tool {
    private final Path basePath;

    public SearchFileContentTool(FileSystemProperties properties) {
        this.basePath = Path.of(properties.basePath()).toAbsolutePath();
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
    public ToolResult execute(ToolRequest request) {
        Object rawDir = request.arguments().get("directory");
        Object rawQuery = request.arguments().get("query");
        Object rawIsRegex = request.arguments().get("isRegex");

        if (!(rawDir instanceof String dirStr) || dirStr.isBlank()) {
            return new ToolResult(false, "search_file_content requires a non-blank directory argument", Map.of());
        }
        if (!(rawQuery instanceof String queryStr) || queryStr.isEmpty()) {
            return new ToolResult(false, "search_file_content requires a non-empty query argument", Map.of());
        }
        if (!(rawIsRegex instanceof Boolean isRegex)) {
            return new ToolResult(false, "search_file_content requires a boolean isRegex argument", Map.of());
        }

        try {
            Path searchRoot = FileSystemUtils.resolveSandboxed(basePath, dirStr);
            if (!Files.exists(searchRoot)) {
                return new ToolResult(false, "Directory does not exist: " + dirStr, Map.of());
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
                return new ToolResult(true, "No matches found.", Map.of("matches", 0));
            }

            String output = String.join("\n", results);
            if (results.size() >= maxResults) {
                output += "\n\n(Results truncated to " + maxResults + " matches)";
            }
            
            return new ToolResult(true, "Matches found:\n" + output, Map.of("matches", results.size()));

        } catch (IllegalArgumentException e) {
            return new ToolResult(false, e.getMessage(), Map.of());
        } catch (IOException e) {
            return new ToolResult(false, "Failed to search files: " + e.getMessage(), Map.of());
        }
    }
}
