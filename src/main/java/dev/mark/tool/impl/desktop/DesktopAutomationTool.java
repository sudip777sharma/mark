package dev.mark.tool.impl.desktop;

import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Desktop automation tool with focus-awareness.
 *
 * <p>Provides robust desktop automation capabilities by implementing {@link Tool}
 * and leveraging Java's {@link Robot} class alongside native Win32 API calls via PowerShell scripts.
 *
 * <p>Supports focus verification: when {@code target_window} is provided, verifies that
 * the window is in the foreground before executing state-changing input actions.
 * Also supports multi-key combinations (e.g. 'CTRL+F', 'CTRL+V') and clipboard-based instant paste.
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
        return "Automate the desktop by moving the mouse, clicking, typing text, pasting text via clipboard, "
             + "pressing key combinations (e.g. 'CTRL+F', 'CTRL+V', 'ENTER', 'ESCAPE'), or waiting. "
             + "Supports focus verification: set 'target_window' to a window title substring and the tool "
             + "will verify (and restore) focus before acting. Also supports 'focus' action to bring a window "
             + "to the foreground by title, and 'activate_and_click' for focus-then-click.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "action", Map.of(
                                "type", "string",
                                "description", "Action: 'move', 'click', 'type', 'paste', 'press', 'delay', 'focus', or 'activate_and_click'"),
                        "x", Map.of("type", "integer", "description", "X coordinate (for 'move', 'click', 'activate_and_click')"),
                        "y", Map.of("type", "integer", "description", "Y coordinate (for 'move', 'click', 'activate_and_click')"),
                        "button", Map.of("type", "string", "description", "Mouse button for 'click': 'left', 'right', 'middle'"),
                        "text", Map.of("type", "string", "description", "Text to type or paste (for 'type' and 'paste' actions)"),
                        "key", Map.of("type", "string", "description", "Key or key combination for 'press' (e.g. 'ENTER', 'TAB', 'ESCAPE', 'CTRL+F', 'CTRL+V')"),
                        "duration", Map.of("type", "integer", "description", "Milliseconds to wait (for 'delay')"),
                        "target_window", Map.of("type", "string", "description",
                                "Optional window title substring. When set, focus is verified/restored before the action.")
                ),
                "required", List.of("action")
        );
    }

    @Override
    public ToolResultDTO execute(ToolRequestDTO request) {
        Map<String, Object> arguments = request.arguments();
        String action = (String) arguments.get("action");
        if (action == null) return new ToolResultDTO(false, "Action is required.", Map.of());

        String targetWindow = (String) arguments.get("target_window");

        try {
            // Focus-independent actions
            if ("delay".equalsIgnoreCase(action)) {
                Integer duration = arguments.containsKey("duration")
                        ? ((Number) arguments.get("duration")).intValue() : 1000;
                robot.delay(duration);
                return ToolResultDTO.success("Delayed for " + duration + " ms");
            }

            if ("focus".equalsIgnoreCase(action)) {
                String title = targetWindow != null ? targetWindow : (String) arguments.get("text");
                if (title == null || title.isBlank()) {
                    return new ToolResultDTO(false, "'focus' requires 'target_window' or 'text' with the window title", Map.of());
                }
                return bringToForeground(title);
            }

            // Focus-dependent actions: verify target window first
            if (targetWindow != null && !targetWindow.isBlank()) {
                ToolResultDTO focusCheck = ensureFocus(targetWindow);
                if (!focusCheck.successful()) {
                    return focusCheck;  // abort if focus could not be restored
                }
            }

            switch (action.toLowerCase()) {
                case "move":
                    if (!arguments.containsKey("x") || !arguments.containsKey("y")) {
                        return new ToolResultDTO(false, "X and Y coordinates are required for 'move'", Map.of());
                    }
                    int x = ((Number) arguments.get("x")).intValue();
                    int y = ((Number) arguments.get("y")).intValue();
                    robot.mouseMove(x, y);
                    return ToolResultDTO.success("Moved mouse to " + x + ", " + y);

                case "click":
                    String button = arguments.containsKey("button") ? (String) arguments.get("button") : "left";
                    int mask = buttonMask(button);
                    robot.mousePress(mask);
                    robot.mouseRelease(mask);
                    return ToolResultDTO.success("Clicked " + button + " mouse button");

                case "activate_and_click":
                    if (!arguments.containsKey("x") || !arguments.containsKey("y")) {
                        return new ToolResultDTO(false, "x and y are required for 'activate_and_click'", Map.of());
                    }
                    int ax = ((Number) arguments.get("x")).intValue();
                    int ay = ((Number) arguments.get("y")).intValue();
                    robot.mouseMove(ax, ay);
                    robot.delay(100);
                    int aBtn = buttonMask(arguments.containsKey("button") ? (String) arguments.get("button") : "left");
                    robot.mousePress(aBtn);
                    robot.mouseRelease(aBtn);
                    return ToolResultDTO.success("Activated and clicked at " + ax + ", " + ay);

                case "type":
                    String text = (String) arguments.get("text");
                    if (text == null) return new ToolResultDTO(false, "Text is required for 'type'", Map.of());
                    typeString(text);
                    return ToolResultDTO.success("Typed text: " + text);

                case "paste":
                    String pasteText = (String) arguments.get("text");
                    if (pasteText == null) return new ToolResultDTO(false, "Text is required for 'paste'", Map.of());
                    setClipboardText(pasteText);
                    robot.delay(50);
                    pressKeyCombination("CTRL+V");
                    return ToolResultDTO.success("Pasted text via clipboard: " + pasteText);

                case "press":
                    String key = (String) arguments.get("key");
                    if (key == null) return new ToolResultDTO(false, "Key name is required for 'press'", Map.of());
                    pressKeyCombination(key);
                    return ToolResultDTO.success("Pressed key: " + key);

                default:
                    return new ToolResultDTO(false, "Unknown action: " + action, Map.of());
            }
        } catch (Exception e) {
            return new ToolResultDTO(false, "Failed to execute desktop action: " + e.getMessage(), Map.of());
        }
    }

    /**
     * Sets the system clipboard text content with retries for Windows clipboard contention.
     */
    public static void setClipboardText(String text) {
        StringSelection selection = new StringSelection(text != null ? text : "");
        for (int i = 0; i < 5; i++) {
            try {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(selection, selection);
                return;
            } catch (IllegalStateException e) {
                try {
                    Thread.sleep(60);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } catch (Exception e) {
                break;
            }
        }
    }

    /**
     * Checks if the foreground window title contains the target string.
     * If not, attempts to bring it to the foreground once.
     */
    public ToolResultDTO ensureFocus(String targetTitle) {
        String currentTitle = getForegroundWindowTitle();
        String safe = sanitize(targetTitle);

        if (currentTitle != null && currentTitle.toLowerCase().contains(safe.toLowerCase())) {
            return ToolResultDTO.success("Focus verified: " + currentTitle);
        }

        log.info("Target '{}' is not in foreground (current: '{}'). Attempting recovery.", safe, currentTitle);
        ToolResultDTO bringResult = bringToForeground(safe);
        if (!bringResult.successful()) {
            return new ToolResultDTO(false,
                "FOCUS LOST: Target window '" + safe + "' is not in the foreground and could not be restored. "
                + "Current foreground: '" + currentTitle + "'. Action aborted to prevent misplaced input.",
                Map.of());
        }

        // Verify again after restore
        robot.delay(500);
        String afterTitle = getForegroundWindowTitle();
        if (afterTitle != null && afterTitle.toLowerCase().contains(safe.toLowerCase())) {
            return ToolResultDTO.success("Focus restored to: " + afterTitle);
        }

        return ToolResultDTO.success("Focus operation sent for: " + safe);
    }

    /**
     * Inspects the title of the current foreground window.
     */
    public String getForegroundWindowTitle() {
        String script = String.join("\n",
            "Add-Type -TypeDefinition @'",
            "using System; using System.Runtime.InteropServices; using System.Text;",
            "public class FW {",
            "  [DllImport(\"user32.dll\")] public static extern IntPtr GetForegroundWindow();",
            "  [DllImport(\"user32.dll\")] public static extern int GetWindowText(IntPtr h, StringBuilder t, int c);",
            "}",
            "'@",
            "$h = [FW]::GetForegroundWindow()",
            "if ($h -eq [IntPtr]::Zero) { exit }",
            "$t = New-Object Text.StringBuilder 256",
            "[FW]::GetWindowText($h, $t, 256) | Out-Null",
            "$t.ToString()"
        );
        try {
            return runPowershell(script).trim();
        } catch (Exception e) {
            log.warn("Failed to get foreground window title: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Searches for a visible window matching targetTitle and brings it to foreground.
     */
    public ToolResultDTO bringToForeground(String targetTitle) {
        String safe = sanitize(targetTitle);
        String script = String.join("\n",
            "Add-Type -TypeDefinition @'",
            "using System; using System.Runtime.InteropServices; using System.Text;",
            "public class WA {",
            "  [DllImport(\"user32.dll\")] public static extern bool SetForegroundWindow(IntPtr hWnd);",
            "  [DllImport(\"user32.dll\")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);",
            "  public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);",
            "  [DllImport(\"user32.dll\")] public static extern bool EnumWindows(EnumWindowsProc cb, IntPtr lParam);",
            "  [DllImport(\"user32.dll\")] public static extern int GetWindowText(IntPtr h, StringBuilder t, int c);",
            "  [DllImport(\"user32.dll\")] public static extern int GetClassName(IntPtr h, StringBuilder c, int n);",
            "  [DllImport(\"user32.dll\")] public static extern bool IsWindowVisible(IntPtr h);",
            "}",
            "'@",
            "$found=$false",
            "[WA]::EnumWindows({param($h,$l)",
            "  if(-not [WA]::IsWindowVisible($h)){return $true}",
            "  $c=New-Object Text.StringBuilder 256",
            "  [WA]::GetClassName($h,$c,256)|Out-Null",
            "  if('" + safe + "' -like '*WhatsApp*' -and $c.ToString() -like '*Chrome*'){return $true}",
            "  $t=New-Object Text.StringBuilder 256",
            "  [WA]::GetWindowText($h,$t,256)|Out-Null",
            "  if($t.ToString() -like '*" + safe + "*'){",
            "    $cmd = if('" + safe + "' -like '*WhatsApp*'){ 3 } else { 9 }",
            "    [WA]::ShowWindow($h,$cmd)|Out-Null",
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
                return new ToolResultDTO(false, output, Map.of());
            }
            return ToolResultDTO.success(output);
        } catch (Exception e) {
            return new ToolResultDTO(false, "Focus restore failed: " + e.getMessage(), Map.of());
        }
    }

    private String sanitize(String input) {
        return input.replaceAll("[^a-zA-Z0-9 _\\-]", "");
    }

    String runPowershell(String script) throws IOException, InterruptedException {
        log.info("Executing DesktopAutomation PowerShell script:\n{}", script);
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
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.info("PowerShell Output:\n{}", output);
            return output;
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

    /**
     * Executes single key presses or key combinations like "CTRL+F", "CTRL+V", "ALT+TAB".
     */
    public void pressKeyCombination(String keyCombo) {
        if (keyCombo == null || keyCombo.isBlank()) return;
        String[] parts = keyCombo.split("\\+");
        List<Integer> pressedKeys = new ArrayList<>();
        try {
            for (String part : parts) {
                int code = resolveKeyCode(part.trim());
                robot.keyPress(code);
                pressedKeys.add(code);
                robot.delay(20);
            }
        } finally {
            for (int i = pressedKeys.size() - 1; i >= 0; i--) {
                try {
                    robot.keyRelease(pressedKeys.get(i));
                    robot.delay(10);
                } catch (Exception ignored) {}
            }
        }
    }

    public int resolveKeyCode(String keyName) {
        String upper = keyName.toUpperCase();
        return switch (upper) {
            case "ENTER", "RETURN" -> KeyEvent.VK_ENTER;
            case "TAB" -> KeyEvent.VK_TAB;
            case "SPACE" -> KeyEvent.VK_SPACE;
            case "ESCAPE", "ESC" -> KeyEvent.VK_ESCAPE;
            case "CTRL", "CONTROL" -> KeyEvent.VK_CONTROL;
            case "ALT" -> KeyEvent.VK_ALT;
            case "SHIFT" -> KeyEvent.VK_SHIFT;
            case "WINDOWS", "WIN", "SUPER" -> KeyEvent.VK_WINDOWS;
            case "UP" -> KeyEvent.VK_UP;
            case "DOWN" -> KeyEvent.VK_DOWN;
            case "LEFT" -> KeyEvent.VK_LEFT;
            case "RIGHT" -> KeyEvent.VK_RIGHT;
            case "BACKSPACE" -> KeyEvent.VK_BACK_SPACE;
            case "DELETE", "DEL" -> KeyEvent.VK_DELETE;
            case "PAGE_UP", "PAGEUP" -> KeyEvent.VK_PAGE_UP;
            case "PAGE_DOWN", "PAGEDOWN" -> KeyEvent.VK_PAGE_DOWN;
            case "HOME" -> KeyEvent.VK_HOME;
            case "END" -> KeyEvent.VK_END;
            default -> {
                if (upper.startsWith("F") && upper.length() >= 2) {
                    try {
                        int fNum = Integer.parseInt(upper.substring(1));
                        if (fNum >= 1 && fNum <= 12) {
                            yield KeyEvent.VK_F1 + (fNum - 1);
                        }
                    } catch (NumberFormatException ignored) {}
                }
                if (upper.length() == 1) {
                    char ch = upper.charAt(0);
                    if (ch >= 'A' && ch <= 'Z') {
                        yield KeyEvent.VK_A + (ch - 'A');
                    }
                    if (ch >= '0' && ch <= '9') {
                        yield KeyEvent.VK_0 + (ch - '0');
                    }
                    int extended = KeyEvent.getExtendedKeyCodeForChar(ch);
                    if (extended != KeyEvent.CHAR_UNDEFINED) {
                        yield extended;
                    }
                }
                throw new IllegalArgumentException("Unsupported key: " + keyName);
            }
        };
    }
}
