package dev.mark.tool.impl.desktop;

import dev.mark.agent.model.ui.UiElementModel;
import dev.mark.tool.dto.ToolResultDTO;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class DesktopActionExecutorService {

    private final Robot robot;

    public DesktopActionExecutorService() {
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException("Failed to init Robot", e);
        }
    }

    public ToolResultDTO executeClick(UiElementModel element, String buttonName) {
        if (element.name() != null && !element.name().isBlank() && element.role() != null) {
            boolean invoked = tryUiAutomationInvoke(element);
            if (invoked) {
                return ToolResultDTO.success("Successfully invoked " + element.name() + " via UIAutomation");
            }
        }

        if (element.bounds() != null && element.bounds().width() > 0 && element.bounds().height() > 0) {
            int centerX = element.bounds().x() + (element.bounds().width() / 2);
            int centerY = element.bounds().y() + (element.bounds().height() / 2);
            robot.mouseMove(centerX, centerY);
            robot.delay(50);
            int mask = getButtonMask(buttonName);
            robot.mousePress(mask);
            robot.mouseRelease(mask);
            return ToolResultDTO.success("Successfully clicked element at coordinates " + centerX + "," + centerY);
        }

        return new ToolResultDTO(false, "Failed to click element: UIAutomation failed and no valid coordinates.", Map.of());
    }

    public ToolResultDTO executeType(UiElementModel element, String text) {
        executeClick(element, "left");
        robot.delay(100);

        for (char c : text.toCharArray()) {
            int keyCode = KeyEvent.getExtendedKeyCodeForChar(c);
            if (KeyEvent.CHAR_UNDEFINED == keyCode) continue;
            
            boolean upper = Character.isUpperCase(c);
            if (upper) robot.keyPress(KeyEvent.VK_SHIFT);
            robot.keyPress(keyCode);
            robot.keyRelease(keyCode);
            if (upper) robot.keyRelease(KeyEvent.VK_SHIFT);
            robot.delay(10);
        }

        return ToolResultDTO.success("Typed text into element.");
    }
    
    public ToolResultDTO executeWait(int durationMs) {
        robot.delay(durationMs);
        return ToolResultDTO.success("Waited for " + durationMs + "ms.");
    }

    private int getButtonMask(String button) {
        if ("right".equalsIgnoreCase(button)) return InputEvent.BUTTON3_DOWN_MASK;
        if ("middle".equalsIgnoreCase(button)) return InputEvent.BUTTON2_DOWN_MASK;
        return InputEvent.BUTTON1_DOWN_MASK;
    }

    private boolean tryUiAutomationInvoke(UiElementModel element) {
        String safeName = element.name().replace("'", "''");
        String safeRole = element.role();

        String script = String.join("\n",
            "Add-Type -AssemblyName UIAutomationClient",
            "$root = [System.Windows.Automation.AutomationElement]::RootElement",
            "$nameCond = New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::NameProperty, '" + safeName + "')",
            "$typeCond = New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::ControlTypeProperty, [System.Windows.Automation.ControlType]::" + safeRole + ")",
            "$andCond = New-Object System.Windows.Automation.AndCondition($nameCond, $typeCond)",
            "$el = $root.FindFirst([System.Windows.Automation.TreeScope]::Descendants, $andCond)",
            "if ($el -eq $null) { exit 1 }",
            "try {",
            "  $pat = $el.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern) -as [System.Windows.Automation.InvokePattern]",
            "  if ($pat -eq $null) { exit 2 }",
            "  $pat.Invoke()",
            "  exit 0",
            "} catch {",
            "  exit 3",
            "}"
        );

        try {
            java.nio.file.Path tempScript = java.nio.file.Files.createTempFile("invoke_", ".ps1");
            try {
                java.nio.file.Files.writeString(tempScript, script, StandardCharsets.UTF_8);
                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", tempScript.toString());
                Process p = pb.start();
                if (p.waitFor(3, TimeUnit.SECONDS)) {
                    return p.exitValue() == 0;
                } else {
                    p.destroyForcibly();
                    return false;
                }
            } finally {
                java.nio.file.Files.deleteIfExists(tempScript);
            }
        } catch (Exception e) {
            return false;
        }
    }

    public boolean checkElementExists(UiElementModel element) {
        if (element.name() == null || element.name().isBlank() || element.role() == null) {
            return true; // Cannot verify cheaply, assume valid
        }

        String safeName = element.name().replace("'", "''");
        String safeRole = element.role();

        String script = String.join("\n",
            "Add-Type -AssemblyName UIAutomationClient",
            "$root = [System.Windows.Automation.AutomationElement]::RootElement",
            "$nameCond = New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::NameProperty, '" + safeName + "')",
            "$typeCond = New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::ControlTypeProperty, [System.Windows.Automation.ControlType]::" + safeRole + ")",
            "$andCond = New-Object System.Windows.Automation.AndCondition($nameCond, $typeCond)",
            "$el = $root.FindFirst([System.Windows.Automation.TreeScope]::Descendants, $andCond)",
            "if ($el -eq $null) { exit 1 } else { exit 0 }"
        );

        try {
            java.nio.file.Path tempScript = java.nio.file.Files.createTempFile("check_", ".ps1");
            try {
                java.nio.file.Files.writeString(tempScript, script, StandardCharsets.UTF_8);
                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", tempScript.toString());
                Process p = pb.start();
                if (p.waitFor(2, TimeUnit.SECONDS)) {
                    return p.exitValue() == 0;
                } else {
                    p.destroyForcibly();
                    return false;
                }
            } finally {
                java.nio.file.Files.deleteIfExists(tempScript);
            }
        } catch (Exception e) {
            return false;
        }
    }
}
