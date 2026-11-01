package dev.mark.tool.impl.file;

import dev.mark.tool.util.FileSystemUtils;
import dev.mark.tool.config.FileSystemPropertiesConfig;
import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ReadFileTool implements Tool {

    private static final long MAX_SIZE_BYTES = 100 * 1024;
    private final Path basePath;

    public ReadFileTool(dev.mark.preference.UserPreferenceService userPreferenceService, FileSystemPropertiesConfig properties) {
        this.basePath = Path.of(userPreferenceService.getString("filesystem.basePath", properties.basePath())).toAbsolutePath();
    }

    @Override public String name() { return "read_file"; }

    @Override public String description() {
        return "Read the contents of a file. Strictly for individual files (NOT directories; for folders use list_directory). Path is relative to the workspace root or Desktop.";
    }

    @Override public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of(
                                "type", "string",
                                "description", "Relative path of the specific file to read. Strictly for files; for folders use list_directory.")),
                "required", List.of("path"));
    }

    @Override
    public ToolResultDTO execute(ToolRequestDTO request) {
        Object raw = request.arguments().get("path");
        if (!(raw instanceof String pathStr) || pathStr.isBlank()) {
            return new ToolResultDTO(false, "read_file requires a non-blank string path argument", Map.of());
        }
        try {
            Path resolved;
            try {
                resolved = FileSystemUtils.resolveSandboxed(basePath, pathStr);
            } catch (IllegalArgumentException e) {
                resolved = null;
            }

            if (resolved == null || !Files.exists(resolved)) {
                Path desktopFallback = resolveDesktopPath(pathStr);
                if (desktopFallback != null && Files.exists(desktopFallback)) {
                    resolved = desktopFallback;
                } else {
                    return new ToolResultDTO(false, "File not found: " + pathStr, Map.of());
                }
            }
            if (!Files.isRegularFile(resolved)) {
                return new ToolResultDTO(false, "'" + pathStr + "' is a directory/folder, not a file. To view or list files in this directory, you must call tool 'list_directory'.", Map.of());
            }
            long size = Files.size(resolved);
            if (size > MAX_SIZE_BYTES) {
                return new ToolResultDTO(false, "File too large (" + size + " bytes, max " + MAX_SIZE_BYTES + ")", Map.of());
            }
            String content = Files.readString(resolved);
            return new ToolResultDTO(true, "File content (" + size + " bytes):\n" + content,
                    Map.of("path", pathStr, "sizeBytes", size));
        } catch (IllegalArgumentException e) {
            return new ToolResultDTO(false, e.getMessage(), Map.of());
        } catch (IOException e) {
            return new ToolResultDTO(false, "Failed to read file: " + e.getMessage(), Map.of());
        }
    }

    private Path resolveDesktopPath(String pathStr) {
        String stripped = pathStr.replace('\\', '/');
        if (stripped.toLowerCase().startsWith("desktop/")) {
            stripped = stripped.substring(8);
        } else if (stripped.equalsIgnoreCase("desktop")) {
            stripped = "";
        } else if (stripped.toLowerCase().startsWith("./desktop/")) {
            stripped = stripped.substring(10);
        } else {
            return null;
        }

        String userHome = System.getProperty("user.home", "");
        if (!userHome.isBlank()) {
            Path oneDriveDesktop = Paths.get(userHome, "OneDrive", "Desktop").resolve(stripped).normalize();
            if (Files.exists(oneDriveDesktop)) return oneDriveDesktop;
            Path stdDesktop = Paths.get(userHome, "Desktop").resolve(stripped).normalize();
            if (Files.exists(stdDesktop)) return stdDesktop;
        }
        return null;
    }
}
