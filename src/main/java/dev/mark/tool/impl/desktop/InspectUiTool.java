package dev.mark.tool.impl.desktop;

import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * **1. What the class does**
 * Inspects the User Interface (UI) element tree of active Windows applications
 * in a secure, read-only manner using PowerShell and the Windows Automation
 * API.
 *
 * **2. Why it is useful**
 * Enables LLM-driven or agentic desktop workflows to "see" and understand the
 * current state of the Windows desktop environment without risking unauthorized
 * system modifications.
 *
 * **3. How it fits in the flow of the application**
 * Implements the Tool interface. It is registered and invoked by a core
 * execution engine processing ToolRequestDTO commands, returning a
 * ToolResultDTO containing UI data for downstream LLM reasoning.
 *
 * **4. Methods and variables and how they are useful**
 * - **TIMEOUT_SECONDS**: Constant (45s) that prevents execution hangs.
 * - **LIST_WINDOWS_SCRIPT, INSPECT_ACTIVE_SCRIPT, INSPECT_BY_TITLE_TEMPLATE,
 * GET_ACTIVE_WINDOW_SCRIPT**: Pre-baked, read-only PowerShell scripts used to
 * query window details and UI trees safely.
 * - **name(), description(), parameterSchema()**: Provide metadata and
 * parameter validation rules for tool routing.
 * - **execute(ToolRequestDTO)**: Entry point that validates arguments and
 * routes the request to the correct script execution.
 * - **sanitizeForLiteral(String)**: Strips unsafe characters from user input to
 * prevent PowerShell injection.
 * - **runScript(String)**: Writes scripts to temporary files, executes them via
 * ProcessBuilder with restrictive flags, handles timeouts, truncates large
 * outputs, and ensures cleanup.
 *
 * **5. Logic and how it fits into the overall application logic**
 * The class acts as a secure bridge between the application and the OS. When
 * executed, it maps the requested action to a hard-coded PowerShell script,
 * safely interpolates sanitized parameters, runs the script in an isolated
 * process, and returns the structured console output to the caller.
 */
@Component
public class InspectUiTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(InspectUiTool.class);
    private static final int TIMEOUT_SECONDS = 45;

    // ── Pre-baked, read-only PowerShell scripts ─────────────────────────────

    /**
     * Lists every top-level window visible to UIAutomation.
     * Returns: Name | ClassName | ProcessId — one per line.
     */
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

    /**
     * Dumps the element tree of the foreground window (depth 3).
     * For each element: ControlType, Name, ClassName, BoundingRectangle.
     * The output is indented to show parent→child relationships.
     */
    private static final String INSPECT_ACTIVE_SCRIPT = String.join("\n",
            "Add-Type -AssemblyName UIAutomationClient",
            "Add-Type -AssemblyName UIAutomationTypes",
            "Add-Type @'",
            "using System;",
            "using System.Runtime.InteropServices;",
            "public class FGWin { [DllImport(\"user32.dll\")] public static extern IntPtr GetForegroundWindow(); }",
            "'@",
            "$hwnd = [FGWin]::GetForegroundWindow()",
            "$el = [System.Windows.Automation.AutomationElement]::FromHandle($hwnd)",
            "function Dump($e, $depth) {",
            "  if ($depth -gt 3) { return }",
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

    /**
     * Finds a window whose title contains the supplied search term and dumps
     * its element tree (depth 3). The search term is matched safely using
     * PowerShell's -like operator with wildcards — no script injection is
     * possible because the term is passed as a .NET string literal, not as
     * executable code.
     */
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
            "Write-Output \"Found window: $($target.Current.Name)\"",
            "function Dump($e, $depth) {",
            "  if ($depth -gt 3) { return }",
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

    /**
     * Returns the title and process name of the current foreground window.
     */
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

    // ── Tool interface ──────────────────────────────────────────────────────

    @Override
    public String name() {
        return "inspect_ui";
    }

    @Override
    public String description() {
        return "Read the UI element tree of a Windows application. "
                + "Actions: 'list_windows' (show all open windows), "
                + "'inspect_active' (dump element tree of foreground window), "
                + "'inspect_window' (dump elements of a window matching 'title'), "
                + "'get_active_window' (return title/process of foreground window). "
                + "All operations are read-only.";
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
                case "list_windows" -> runScript(LIST_WINDOWS_SCRIPT);
                case "inspect_active" -> runScript(INSPECT_ACTIVE_SCRIPT);
                case "get_active_window" -> runScript(GET_ACTIVE_WINDOW_SCRIPT);
                case "inspect_window" -> {
                    String title = (String) request.arguments().get("title");
                    if (title == null || title.isBlank()) {
                        yield new ToolResultDTO(false, "'title' is required for inspect_window", Map.of());
                    }
                    // Sanitize: strip anything that could break the PowerShell string literal
                    String safe = sanitizeForLiteral(title);
                    String script = INSPECT_BY_TITLE_TEMPLATE.replace("SEARCH_TERM", safe);
                    yield runScript(script);
                }
                default -> new ToolResultDTO(false, "Unknown inspect_ui action: " + action, Map.of());
            };
        } catch (Exception e) {
            log.error("inspect_ui failed", e);
            return new ToolResultDTO(false, "inspect_ui error: " + e.getMessage(), Map.of());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Removes characters that could break a PowerShell single-quoted string
     * or be used for injection. Only letters, digits, spaces, hyphens, and
     * underscores survive.
     */
    private String sanitizeForLiteral(String input) {
        return input.replaceAll("[^a-zA-Z0-9 _\\-]", "");
    }

    private ToolResultDTO runScript(String script) throws IOException, InterruptedException {
        // Write script to a temp file to avoid Windows command-line quoting issues.
        // Using -File instead of -Command ensures PowerShell parses the script
        // correctly without the cmd.exe argument parser stripping inner quotes.
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
                return new ToolResultDTO(false, "inspect_ui timed out after " + TIMEOUT_SECONDS + "s", Map.of());
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.exitValue();

            if (output.isEmpty()) {
                output = "(no output — the script returned nothing)";
            }

            // Truncate very large trees to avoid blowing up the LLM context
            if (output.length() > 8000) {
                output = output.substring(0, 8000) + "\n... (output truncated at 8000 chars)";
            }

            if (exitCode != 0) {
                return new ToolResultDTO(false, "Script exited with code " + exitCode + ":\n" + output, Map.of());
            }
            return ToolResultDTO.success(output);
        } finally {
            try {
                java.nio.file.Files.deleteIfExists(tempScript);
            } catch (IOException ignored) {
            }
        }
    }
}
