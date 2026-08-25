package dev.mark.tool;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Desktop automation tool with focus-awareness.
 *
 * <p>When the optional {@code target_window} parameter is provided, the tool
 * verifies that the named window is in the foreground before executing any
 * input action (click, type, press, move).  If the window has lost focus the
 * tool attempts to bring it back automatically using a read-only PowerShell
 * snippet.  If focus cannot be restored the action is aborted with a clear
 * error message so the LLM can re-plan.
 *
 * <p>The {@code delay} action always succeeds regardless of focus.
 *
 * <p><b>Safety:</b> The only PowerShell scripts executed here are hard-coded,
 * read-only snippets.  The window title is sanitised to alphanumeric
 * characters before insertion.
 */
@Component
public class DesktopAutomationTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(DesktopAutomationTool.class);
    private final Robot robot;

    public DesktopAutomationTool() {
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException("Failed to initialize java.awt.Robot for DesktopAutomationTool", e);
        }
    }

    @Override
    public String name() {
        return "desktop_automation";
    }

    @Override
    public String description() {
        return "Automate the desktop by moving the mouse, clicking, typing text, pressing keys, "
             + "or waiting. Supports focus verification: set 'target_window' to a window title "
             + "substring and the tool will verify (and restore) focus before acting. "
             + "Also supports 'focus' action to bring a window to the foreground by title. "
             + "Use 'activate_and_click' for a combined focus-then-click-at-coordinates action.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "action", Map.of(
                                "type", "string",
                                "description", "Action: 'move', 'click', 'type', 'press', 'delay', 'focus', or 'activate_and_click'"),
                        "x", Map.of("type", "integer", "description", "X coordinate (for 'move', 'click', 'activate_and_click')"),
                        "y", Map.of("type", "integer", "description", "Y coordinate (for 'move', 'click', 'activate_and_click')"),
                        "button", Map.of("type", "string", "description", "Mouse button for 'click': 'left', 'right', 'middle'"),
                        "text", Map.of("type", "string", "description", "Text to type (for 'type' action)"),
                        "key", Map.of("type", "string", "description", "Key name for 'press' (e.g. 'ENTER', 'TAB', 'WINDOWS')"),
                        "duration", Map.of("type", "integer", "description", "Milliseconds to wait (for 'delay')"),
                        "target_window", Map.of("type", "string", "description",
                                "Optional window title substring. When set, focus is verified/restored before the action.")
                ),
                "required", java.util.List.of("action")
        );
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        Map<String, Object> arguments = request.arguments();
        String action = (String) arguments.get("action");
        if (action == null) return new ToolResult(false, "Action is required.", Map.of());

        String targetWindow = (String) arguments.get("target_window");

        try {
            // ── Focus-independent actions ──
            if ("delay".equalsIgnoreCase(action)) {
                Integer duration = arguments.containsKey("duration")
                        ? ((Number) arguments.get("duration")).intValue() : 1000;
                robot.delay(duration);
                return ToolResult.success("Delayed for " + duration + " ms");
            }

            if ("focus".equalsIgnoreCase(action)) {
                String title = targetWindow != null ? targetWindow : (String) arguments.get("text");
                if (title == null || title.isBlank()) {
                    return new ToolResult(false, "'focus' requires 'target_window' or 'text' with the window title", Map.of());
                }
                return bringToForeground(title);
            }

            // ── Focus-dependent actions: verify target window first ──
            if (targetWindow != null && !targetWindow.isBlank()) {
                ToolResult focusCheck = ensureFocus(targetWindow);
                if (!focusCheck.successful()) {
                    return focusCheck;  // abort — focus could not be restored
                }
            }

            switch (action.toLowerCase()) {
                case "move":
                    if (!arguments.containsKey("x") || !arguments.containsKey("y")) {
                        return new ToolResult(false, "X and Y coordinates are required for 'move'", Map.of());
                    }
                    int x = ((Number) arguments.get("x")).intValue();
                    int y = ((Number) arguments.get("y")).intValue();
                    robot.mouseMove(x, y);
                    return ToolResult.success("Moved mouse to " + x + ", " + y);

                case "click":
                    String button = arguments.containsKey("button") ? (String) arguments.get("button") : "left";
                    int mask = buttonMask(button);
                    robot.mousePress(mask);
                    robot.mouseRelease(mask);
                    return ToolResult.success("Clicked " + button + " mouse button");

                case "activate_and_click":
                    if (!arguments.containsKey("x") || !arguments.containsKey("y")) {
                        return new ToolResult(false, "x and y are required for 'activate_and_click'", Map.of());
                    }
                    int ax = ((Number) arguments.get("x")).intValue();
                    int ay = ((Number) arguments.get("y")).intValue();
                    robot.mouseMove(ax, ay);
                    robot.delay(100);
                    int aBtn = buttonMask(arguments.containsKey("button") ? (String) arguments.get("button") : "left");
                    robot.mousePress(aBtn);
                    robot.mouseRelease(aBtn);
                    return ToolResult.success("Activated and clicked at " + ax + ", " + ay);

                case "type":
                    String text = (String) arguments.get("text");
                    if (text == null) return new ToolResult(false, "Text is required for 'type'", Map.of());
                    typeString(text);
                    return ToolResult.success("Typed text: " + text);

                case "press":
                    String key = (String) arguments.get("key");
                    if (key == null) return new ToolResult(false, "Key name is required for 'press'", Map.of());
                    pressKeyByName(key);
                    return ToolResult.success("Pressed key: " + key);

                default:
                    return new ToolResult(false, "Unknown action: " + action, Map.of());
            }
        } catch (Exception e) {
            return new ToolResult(false, "Failed to execute desktop action: " + e.getMessage(), Map.of());
        }
    }

    // ── Focus verification & recovery ───────────────────────────────────────

    /**
     * Checks if the foreground window title contains the target string.
     * If not, attempts to bring it to the foreground once.
     */
    private ToolResult ensureFocus(String targetTitle) {
        String currentTitle = getForegroundWindowTitle();
        String safe = sanitize(targetTitle);

        if (currentTitle != null && currentTitle.toLowerCase().contains(safe.toLowerCase())) {
            return ToolResult.success("Focus verified: " + currentTitle);
        }

        log.info("Target '{}' is not in foreground (current: '{}').  Attempting recovery.", safe, currentTitle);
        ToolResult bringResult = bringToForeground(safe);
        if (!bringResult.successful()) {
            return new ToolResult(false,
                "FOCUS LOST: Target window '" + safe + "' is not in the foreground and could not be restored. "
                + "Current foreground: '" + currentTitle + "'. Action aborted to prevent misplaced input.",
                Map.of());
        }

        // Verify again after restore
        robot.delay(500);
        String afterTitle = getForegroundWindowTitle();
        if (afterTitle != null && afterTitle.toLowerCase().contains(safe.toLowerCase())) {
            return ToolResult.success("Focus restored to: " + afterTitle);
        }

        return new ToolResult(false,
            "FOCUS RECOVERY FAILED: Attempted to bring '" + safe + "' to foreground but current window is: '"
            + afterTitle + "'. Action aborted.", Map.of());
    }

    /**
     * Reads the foreground window title via a tiny, read-only PowerShell script.
     */
    private String getForegroundWindowTitle() {
        String script = String.join("\n",
            "Add-Type @'",
            "using System; using System.Runtime.InteropServices; using System.Text;",
            "public class FG { [DllImport(\"user32.dll\")] public static extern IntPtr GetForegroundWindow();",
            "  [DllImport(\"user32.dll\")] public static extern int GetWindowText(IntPtr h, StringBuilder t, int c); }",
            "'@",
            "$h=[FG]::GetForegroundWindow(); $s=New-Object Text.StringBuilder 256;",
            "[FG]::GetWindowText($h,$s,256)|Out-Null; $s.ToString()"
        );
        try {
            return runPowershell(script).trim();
        } catch (Exception e) {
            log.warn("Could not read foreground window title: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Brings a window matching the given title to the foreground.
     * Uses only read-only Win32 calls + SetForegroundWindow.
     */
    private ToolResult bringToForeground(String titleSubstring) {
        String safe = sanitize(titleSubstring);
        // This script finds the first window whose title contains the search
        // term and activates it.  SetForegroundWindow is the lightest possible
        // focus-change API and does NOT modify the system.
        String script = String.join("\n",
            "Add-Type @'",
            "using System; using System.Runtime.InteropServices; using System.Text;",
            "public class WA {",
            "  [DllImport(\"user32.dll\")] public static extern bool SetForegroundWindow(IntPtr hWnd);",
            "  [DllImport(\"user32.dll\")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);",
            "  public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);",
            "  [DllImport(\"user32.dll\")] public static extern bool EnumWindows(EnumWindowsProc cb, IntPtr lParam);",
            "  [DllImport(\"user32.dll\")] public static extern int GetWindowText(IntPtr h, StringBuilder t, int c);",
            "  [DllImport(\"user32.dll\")] public static extern bool IsWindowVisible(IntPtr h);",
            "}",
            "'@",
            "$found=$false",
            "[WA]::EnumWindows({param($h,$l)",
            "  if(-not [WA]::IsWindowVisible($h)){return $true}",
            "  $t=New-Object Text.StringBuilder 256",
            "  [WA]::GetWindowText($h,$t,256)|Out-Null",
            "  if($t.ToString() -like '*" + safe + "*'){",
            "    [WA]::ShowWindow($h,9)|Out-Null",   // SW_RESTORE
            "    [WA]::SetForegroundWindow($h)|Out-Null",
            "    $script:found=$true",
            "    Write-Output \"Activated: $($t.ToString())\"",
            "    return $false",
            "  }",
            "  return $true",
            "},[IntPtr]::Zero)",
            "if(-not $found){Write-Output 'ERROR: Window not found'; exit 1}"
        );
        try {
            String output = runPowershell(script).trim();
            if (output.startsWith("ERROR")) {
                return new ToolResult(false, output, Map.of());
            }
            return ToolResult.success(output);
        } catch (Exception e) {
            return new ToolResult(false, "Focus restore failed: " + e.getMessage(), Map.of());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String sanitize(String input) {
        return input.replaceAll("[^a-zA-Z0-9 _\\-]", "");
    }

    private String runPowershell(String script) throws IOException, InterruptedException {
        java.nio.file.Path tempScript = java.nio.file.Files.createTempFile("mark_desktop_", ".ps1");
        try {
            java.nio.file.Files.writeString(tempScript, script, StandardCharsets.UTF_8);
            ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-File", tempScript.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean done = p.waitFor(10, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return ""; }
            return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            try { java.nio.file.Files.deleteIfExists(tempScript); } catch (IOException ignored) {}
        }
    }

    private int buttonMask(String button) {
        if ("right".equalsIgnoreCase(button))  return InputEvent.BUTTON3_DOWN_MASK;
        if ("middle".equalsIgnoreCase(button)) return InputEvent.BUTTON2_DOWN_MASK;
        return InputEvent.BUTTON1_DOWN_MASK;
    }

    private void typeString(String text) {
        for (char c : text.toCharArray()) {
            int keyCode = KeyEvent.getExtendedKeyCodeForChar(c);
            if (KeyEvent.CHAR_UNDEFINED == keyCode) {
                throw new RuntimeException("Cannot type character: " + c);
            }
            if (Character.isUpperCase(c)) {
                robot.keyPress(KeyEvent.VK_SHIFT);
            }
            robot.keyPress(keyCode);
            robot.keyRelease(keyCode);
            if (Character.isUpperCase(c)) {
                robot.keyRelease(KeyEvent.VK_SHIFT);
            }
            robot.delay(10);
        }
    }

    private void pressKeyByName(String keyName) {
        int keyCode = switch (keyName.toUpperCase()) {
            case "ENTER" -> KeyEvent.VK_ENTER;
            case "TAB" -> KeyEvent.VK_TAB;
            case "SPACE" -> KeyEvent.VK_SPACE;
            case "ESCAPE" -> KeyEvent.VK_ESCAPE;
            case "WINDOWS", "WIN", "SUPER" -> KeyEvent.VK_WINDOWS;
            case "UP" -> KeyEvent.VK_UP;
            case "DOWN" -> KeyEvent.VK_DOWN;
            case "LEFT" -> KeyEvent.VK_LEFT;
            case "RIGHT" -> KeyEvent.VK_RIGHT;
            case "BACKSPACE" -> KeyEvent.VK_BACK_SPACE;
            case "DELETE" -> KeyEvent.VK_DELETE;
            default -> throw new IllegalArgumentException("Unsupported key: " + keyName);
        };
        robot.keyPress(keyCode);
        robot.keyRelease(keyCode);
    }
}
