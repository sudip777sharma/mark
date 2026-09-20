package dev.mark.tool.impl.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mark.agent.model.ui.SemanticObservationModel;
import dev.mark.agent.model.ui.UiTreeModel;
import dev.mark.agent.service.ui.DeterministicIdentityGenerator;
import dev.mark.agent.service.ui.SemanticUiFilter;
import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import dev.mark.tool.impl.desktop.parser.UiTreeParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * InspectUiTool modified for Phase 1.
 */
@Component
public class InspectUiTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(InspectUiTool.class);
    private static final int TIMEOUT_SECONDS = 45;
    
    private final ObjectMapper objectMapper;
    private final dev.mark.agent.service.ui.UiCacheService cacheService;

    public InspectUiTool(ObjectMapper objectMapper, dev.mark.agent.service.ui.UiCacheService cacheService) {
        this.objectMapper = objectMapper;
        this.cacheService = cacheService;
    }

    private static final String LIST_WINDOWS_SCRIPT = String.join("\n",
            "Add-Type -AssemblyName UIAutomationClient",
            "Add-Type -AssemblyName UIAutomationTypes",
            "$root = [System.Windows.Automation.AutomationElement]::RootElement",
            "$cond = [System.Windows.Automation.Condition]::TrueCondition",
            "$wins = $root.FindAll([System.Windows.Automation.TreeScope]::Children, $cond)",
            "foreach ($w in $wins) {",
            "  $n = $w.Current.Name",
            "  $c = $w.Current.ClassName",
            "  $p = $w.Current.ProcessId",
            "  if ($n -and $n.Trim()) { Write-Output \"$n | $c | PID:$p\" }",
            "}");

    private static final String INSPECT_ACTIVE_SCRIPT = String.join("\n",
            "Add-Type -AssemblyName UIAutomationClient",
            "Add-Type -AssemblyName UIAutomationTypes",
            "Add-Type @'",
            "using System;",
            "using System.Runtime.InteropServices;",
            "using System.Text;",
            "public class FGWin { ",
            "  [DllImport(\"user32.dll\")] public static extern IntPtr GetForegroundWindow(); ",
            "  [DllImport(\"user32.dll\")] public static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int count);",
            "}",
            "'@",
            "$hwnd = [FGWin]::GetForegroundWindow()",
            "$sb = New-Object System.Text.StringBuilder 256",
            "[FGWin]::GetWindowText($hwnd, $sb, 256) | Out-Null",
            "Write-Output \"Title: $($sb.ToString())\"",
            "$el = [System.Windows.Automation.AutomationElement]::FromHandle($hwnd)",
            "function Dump($e, $depth) {",
            "  if ($depth -gt 4) { return }",
            "  $indent = '  ' * $depth",
            "  $ct = $e.Current.ControlType.ProgrammaticName",
            "  $nm = $e.Current.Name",
            "  $cls = $e.Current.ClassName",
            "  $rect = $e.Current.BoundingRectangle",
            "  $r = \"[$([int]$rect.X),$([int]$rect.Y),$([int]$rect.Width),$([int]$rect.Height)]\"",
            "  Write-Output \"${indent}${ct} | Name='${nm}' | Class='${cls}' | Rect=${r}\"",
            "  $cond = [System.Windows.Automation.Condition]::TrueCondition",
            "  $kids = $e.FindAll([System.Windows.Automation.TreeScope]::Children, $cond)",
            "  foreach ($k in $kids) { Dump $k ($depth+1) }",
            "}",
            "Dump $el 0");

    private static final String INSPECT_BY_TITLE_TEMPLATE = String.join("\n",
            "Add-Type -AssemblyName UIAutomationClient",
            "Add-Type -AssemblyName UIAutomationTypes",
            "$root = [System.Windows.Automation.AutomationElement]::RootElement",
            "$cond = [System.Windows.Automation.Condition]::TrueCondition",
            "$wins = $root.FindAll([System.Windows.Automation.TreeScope]::Children, $cond)",
            "$target = $null",
            "foreach ($w in $wins) {",
            "  if ($w.Current.Name -like '*SEARCH_TERM*') { $target = $w; break }",
            "}",
            "if (-not $target) { Write-Output 'ERROR: No window found matching the search term.'; exit 0 }",
            "Write-Output \"Title: $($target.Current.Name)\"",
            "function Dump($e, $depth) {",
            "  if ($depth -gt 4) { return }",
            "  $indent = '  ' * $depth",
            "  $ct = $e.Current.ControlType.ProgrammaticName",
            "  $nm = $e.Current.Name",
            "  $cls = $e.Current.ClassName",
            "  $rect = $e.Current.BoundingRectangle",
            "  $r = \"[$([int]$rect.X),$([int]$rect.Y),$([int]$rect.Width),$([int]$rect.Height)]\"",
            "  Write-Output \"${indent}${ct} | Name='${nm}' | Class='${cls}' | Rect=${r}\"",
            "  $cond = [System.Windows.Automation.Condition]::TrueCondition",
            "  $kids = $e.FindAll([System.Windows.Automation.TreeScope]::Children, $cond)",
            "  foreach ($k in $kids) { Dump $k ($depth+1) }",
            "}",
            "Dump $target 0");

    private static final String GET_ACTIVE_WINDOW_SCRIPT = String.join("\n",
            "Add-Type @'",
            "using System;",
            "using System.Runtime.InteropServices;",
            "using System.Text;",
            "public class WinInfo {",
            "  [DllImport(\"user32.dll\")] public static extern IntPtr GetForegroundWindow();",
            "  [DllImport(\"user32.dll\")] public static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int count);",
            "  [DllImport(\"user32.dll\", SetLastError=true)] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint pid);",
            "}",
            "'@",
            "$hwnd = [WinInfo]::GetForegroundWindow()",
            "$sb = New-Object System.Text.StringBuilder 256",
            "[WinInfo]::GetWindowText($hwnd, $sb, 256) | Out-Null",
            "$processId = 0",
            "[WinInfo]::GetWindowThreadProcessId($hwnd, [ref]$processId) | Out-Null",
            "$proc = (Get-Process -Id $processId -ErrorAction SilentlyContinue).ProcessName",
            "Write-Output \"Title: $($sb.ToString())\"",
            "Write-Output \"Process: $proc\"",
            "Write-Output \"PID: $processId\"");

    @Override
    public String name() {
        return "inspect_ui";
    }

    @Override
    public String description() {
        return "Read the semantic UI element tree of a Windows application. "
                + "Actions: 'list_windows' (show all open windows), "
                + "'inspect_active' (dump actionable elements of foreground window), "
                + "'inspect_window' (dump actionable elements of a window matching 'title'), "
                + "'get_active_window' (return title/process of foreground window).";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "action", Map.of(
                                "type", "string",
                                "description",
                                "One of: list_windows, inspect_active, inspect_window, get_active_window"),
                        "title", Map.of(
                                "type", "string",
                                "description",
                                "Window title substring to search for (only for inspect_window action)")),
                "required", List.of("action"));
    }

    @Override
    public ToolResultDTO execute(ToolRequestDTO request) {
        String action = (String) request.arguments().get("action");
        if (action == null || action.isBlank()) {
            return new ToolResultDTO(false, "inspect_ui requires an 'action' argument", Map.of());
        }

        try {
            return switch (action.toLowerCase()) {
                case "list_windows" -> runRawScript(LIST_WINDOWS_SCRIPT);
                case "inspect_active" -> runSemanticScript(INSPECT_ACTIVE_SCRIPT, request.taskId() != null ? request.taskId().toString() : null);
                case "get_active_window" -> runRawScript(GET_ACTIVE_WINDOW_SCRIPT);
                case "inspect_window" -> {
                    String title = (String) request.arguments().get("title");
                    if (title == null || title.isBlank()) {
                        yield new ToolResultDTO(false, "'title' is required for inspect_window", Map.of());
                    }
                    String safe = sanitizeForLiteral(title);
                    String script = INSPECT_BY_TITLE_TEMPLATE.replace("SEARCH_TERM", safe);
                    yield runSemanticScript(script, request.taskId() != null ? request.taskId().toString() : null);
                }
                default -> new ToolResultDTO(false, "Unknown inspect_ui action: " + action, Map.of());
            };
        } catch (Exception e) {
            log.error("inspect_ui failed", e);
            return new ToolResultDTO(false, "inspect_ui error: " + e.getMessage(), Map.of());
        }
    }

    private String sanitizeForLiteral(String input) {
        return input.replaceAll("[^a-zA-Z0-9 _\\-]", "");
    }

    private ToolResultDTO runRawScript(String script) throws IOException, InterruptedException {
        ProcessResult res = executePowerShell(script);
        if (res.exitCode != 0) {
            return new ToolResultDTO(false, "Script exited with code " + res.exitCode + ":\n" + res.output, Map.of());
        }
        return ToolResultDTO.success(res.output);
    }

    private ToolResultDTO runSemanticScript(String script, String taskId) throws IOException, InterruptedException {
        ProcessResult res = executePowerShell(script);
        if (res.exitCode != 0) {
            return new ToolResultDTO(false, "Script exited with code " + res.exitCode + ":\n" + res.output, Map.of());
        }

        String title = "Unknown Window";
        for (String line : res.output.split("\n")) {
            if (line.startsWith("Title: ")) {
                title = line.substring("Title: ".length()).trim();
                break;
            }
        }

        UiTreeModel rawTree = UiTreeParser.parse(res.output, "WindowsApp", title);
        UiTreeModel identityTree = DeterministicIdentityGenerator.generateIdentities(rawTree);
        
        dev.mark.agent.model.ui.UiDiffResult diffResult = null;
        if (taskId != null) {
            UiTreeModel previous = cacheService.getLatestTree(taskId);
            if (previous != null) {
                diffResult = dev.mark.agent.service.ui.UiTreeDiffer.diff(previous, identityTree);
            }
            cacheService.cacheTree(taskId, identityTree);
        }
        
        SemanticObservationModel semanticModel = SemanticUiFilter.filter(identityTree);
        if (diffResult != null) {
            semanticModel = new SemanticObservationModel(
                semanticModel.applicationName(),
                semanticModel.windowTitle(),
                diffResult,
                semanticModel.actionableElements()
            );
        }
        String jsonObservation = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(semanticModel);
        
        return ToolResultDTO.success(jsonObservation);
    }

    private ProcessResult executePowerShell(String script) throws IOException, InterruptedException {
        java.nio.file.Path tempScript = java.nio.file.Files.createTempFile("mark_inspect_", ".ps1");
        try {
            java.nio.file.Files.writeString(tempScript, script, StandardCharsets.UTF_8);

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-File", tempScript.toString());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ProcessResult(-1, "Timeout after " + TIMEOUT_SECONDS + "s");
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (output.isEmpty()) output = "(no output)";
            return new ProcessResult(process.exitValue(), output);
        } finally {
            try {
                java.nio.file.Files.deleteIfExists(tempScript);
            } catch (IOException ignored) {}
        }
    }

    private record ProcessResult(int exitCode, String output) {}
}
