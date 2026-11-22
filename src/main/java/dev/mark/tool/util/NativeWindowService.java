package dev.mark.tool.util;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * High-performance native OS window management service using JNA.
 * Provides sub-millisecond window foregrounding and active window inspection on Windows.
 */
@Component
public class NativeWindowService {

    private static final Logger log = LoggerFactory.getLogger(NativeWindowService.class);
    private final boolean isWindows;

    public NativeWindowService() {
        this.isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public boolean isWindows() {
        return isWindows;
    }

    /**
     * Retrieves the title of the current foreground window in < 0.1ms.
     */
    public String getForegroundWindowTitle() {
        if (!isWindows) {
            return "";
        }
        try {
            HWND hwnd = User32.INSTANCE.GetForegroundWindow();
            if (hwnd == null) {
                return "";
            }
            char[] buffer = new char[512];
            int length = User32.INSTANCE.GetWindowText(hwnd, buffer, buffer.length);
            return length > 0 ? new String(buffer, 0, length).trim() : "";
        } catch (Throwable e) {
            log.warn("Error getting foreground window title via JNA: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Searches for a visible top-level window whose title contains targetTitleSubstring
     * (case-insensitive) and brings it to the OS foreground in < 1ms.
     */
    public boolean bringToForeground(String targetTitleSubstring) {
        if (!isWindows || targetTitleSubstring == null || targetTitleSubstring.isBlank()) {
            return false;
        }

        String searchLower = targetTitleSubstring.toLowerCase(Locale.ROOT).trim();
        AtomicReference<HWND> targetHwnd = new AtomicReference<>(null);
        AtomicReference<String> matchedTitle = new AtomicReference<>(null);

        try {
            User32.INSTANCE.EnumWindows((hwnd, data) -> {
                if (!User32.INSTANCE.IsWindowVisible(hwnd)) {
                    return true; // continue enumeration
                }
                char[] titleBuffer = new char[512];
                int len = User32.INSTANCE.GetWindowText(hwnd, titleBuffer, titleBuffer.length);
                if (len > 0) {
                    String title = new String(titleBuffer, 0, len).trim();
                    if (title.toLowerCase(Locale.ROOT).contains(searchLower)) {
                        targetHwnd.set(hwnd);
                        matchedTitle.set(title);
                        return false; // found match, stop enumeration
                    }
                }
                return true;
            }, null);

            HWND hwnd = targetHwnd.get();
            if (hwnd != null) {
                // Restore window if minimized or normal, then set foreground
                User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_RESTORE);
                boolean activated = User32.INSTANCE.SetForegroundWindow(hwnd);
                log.info("Activated native window '{}' (hwnd={}, success={})", matchedTitle.get(), hwnd, activated);
                return true;
            } else {
                log.debug("No visible native window matched substring '{}'", targetTitleSubstring);
                return false;
            }
        } catch (Throwable e) {
            log.error("Failed to activate native window for '{}': {}", targetTitleSubstring, e.getMessage(), e);
            return false;
        }
    }
}
