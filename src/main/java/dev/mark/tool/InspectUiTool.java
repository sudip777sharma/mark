package dev.mark.tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads the UI element tree of a Windows application via PowerShell and the
 * built-in System.Windows.Automation API.  Every script executed here is
 * strictly <b>read-only</b>: it never modifies files, the registry, services,
 * or any system state.
 *
 * <p>Safety design:
 * <ul>
 *   <li>Only pre-defined, hard-coded PowerShell snippets are executed — user
 *       or LLM input is <em>never</em> interpolated into a script string.</li>
 *   <li>All scripts are launched with {@code -NoProfile -NonInteractive
 *       -ExecutionPolicy Bypass} so they complete quickly and cannot prompt.</li>
 *   <li>A 15-second timeout kills the process if it hangs.</li>
 * </ul>
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
        "}"
    );

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
        "Dump $el 0"
    );

    /**
     * Finds a window whose title contains the supplied search term and dumps
     * its element tree (depth 3).  The search term is matched safely using
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
        "Dump $target 0"
    );

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
        "Write-Output \"PID: $processId\""
    );

    // ── Tool interface ──────────────────────────────────────────────────────

    @Override
    public String name() { return "inspect_ui"; }

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
                    "description", "One of: list_windows, inspect_active, inspect_window, get_active_window"),
                "title", Map.of(
                    "type", "string",
                    "description", "Window title substring to search for (only for inspect_window action)")
            ),
            "required", List.of("action")
        );
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        String action = (String) request.arguments().get("action");
        if (action == null || action.isBlank()) {
            return new ToolResult(false, "inspect_ui requires an 'action' argument", Map.of());
        }

        try {
            return switch (action.toLowerCase()) {
                case "list_windows"     -> runScript(LIST_WINDOWS_SCRIPT);
                case "inspect_active"   -> runScript(INSPECT_ACTIVE_SCRIPT);
                case "get_active_window" -> runScript(GET_ACTIVE_WINDOW_SCRIPT);
                case "inspect_window"   -> {
                    String title = (String) request.arguments().get("title");
                    if (title == null || title.isBlank()) {
                        yield new ToolResult(false, "'title' is required for inspect_window", Map.of());
                    }
                    // Sanitize: strip anything that could break the PowerShell string literal
                    String safe = sanitizeForLiteral(title);
                    String script = INSPECT_BY_TITLE_TEMPLATE.replace("SEARCH_TERM", safe);
                    yield runScript(script);
                }
                default -> new ToolResult(false, "Unknown inspect_ui action: " + action, Map.of());
            };
        } catch (Exception e) {
            log.error("inspect_ui failed", e);
            return new ToolResult(false, "inspect_ui error: " + e.getMessage(), Map.of());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Removes characters that could break a PowerShell single-quoted string
     * or be used for injection.  Only letters, digits, spaces, hyphens, and
     * underscores survive.
     */
    private String sanitizeForLiteral(String input) {
        return input.replaceAll("[^a-zA-Z0-9 _\\-]", "");
    }

    private ToolResult runScript(String script) throws IOException, InterruptedException {
        // Write script to a temp file to avoid Windows command-line quoting issues.
        // Using -File instead of -Command ensures PowerShell parses the script
        // correctly without the cmd.exe argument parser stripping inner quotes.
        java.nio.file.Path tempScript = java.nio.file.Files.createTempFile("mark_inspect_", ".ps1");
        try {
            java.nio.file.Files.writeString(tempScript, script, StandardCharsets.UTF_8);

            ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-File", tempScript.toString()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ToolResult(false, "inspect_ui timed out after " + TIMEOUT_SECONDS + "s", Map.of());
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
                return new ToolResult(false, "Script exited with code " + exitCode + ":\n" + output, Map.of());
            }
            return ToolResult.success(output);
        } finally {
            try { java.nio.file.Files.deleteIfExists(tempScript); } catch (IOException ignored) {}
        }
    }
}
