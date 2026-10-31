package dev.mark.tool.impl.desktop;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic automation tool for WhatsApp Desktop on Windows.
 *
 * <p>Automates native WhatsApp Desktop using focus-aware Win32 activation,
 * native keyboard shortcuts, Windows system clipboard injection, and
 * accessibility-based chat reading and verification.
 */
@Component
public class WhatsAppTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppTool.class);
    private static final Pattern TIME_PATTERN = Pattern.compile("\\b(\\d{1,2}:\\d{2}\\s*(?:AM|PM|am|pm)?)\\b");

    private final DesktopAutomationTool desktopAutomation;
    private final ObjectMapper objectMapper;

    public WhatsAppTool(DesktopAutomationTool desktopAutomation, ObjectMapper objectMapper) {
        this.desktopAutomation = desktopAutomation;
        this.objectMapper = objectMapper;
    }

    public record WhatsAppMessage(String sender, String text, String timestamp, String status, String raw) {}

    @Override
    public String name() {
        return "whatsapp_automation";
    }

    @Override
    public String description() {
        return "Deterministic automation skill for WhatsApp Desktop on Windows. "
             + "Actions supported: 'send_message' (send text/emojis to a contact), "
             + "'open_chat' (focus a conversation without sending), "
             + "'read_chat' (extract recent messages from chat, also acts as verifier for send_message). "
             + "Arguments: 'action' (required: 'send_message', 'open_chat', or 'read_chat'), "
             + "'contact' (contact name or phone number), "
             + "'message' (required for 'send_message'), "
             + "'expected_message' (optional for 'read_chat' to verify that a specific message was sent), "
             + "'limit' (optional integer for 'read_chat', default: 10).";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "action", Map.of(
                    "type", "string",
                    "description", "WhatsApp action: 'send_message', 'open_chat', or 'read_chat'"
                ),
                "contact", Map.of(
                    "type", "string",
                    "description", "Contact name or phone number as saved or formatted in WhatsApp (required for send_message/open_chat, optional for read_chat)"
                ),
                "message", Map.of(
                    "type", "string",
                    "description", "Text content to send (required for 'send_message')"
                ),
                "expected_message", Map.of(
                    "type", "string",
                    "description", "Optional text to verify in chat history for 'read_chat' (closed-loop verification of send_message)"
                ),
                "limit", Map.of(
                    "type", "integer",
                    "description", "Number of recent messages to read for 'read_chat' (default: 10)"
                )
            ),
            "required", List.of("action")
        );
    }

    @Override
    public ToolResultDTO execute(ToolRequestDTO request) {
        Map<String, Object> arguments = request.arguments();
        String action = (String) arguments.get("action");
        if (action == null || action.isBlank()) {
            return new ToolResultDTO(false, "Argument 'action' is required.", Map.of());
        }

        String contact = (String) arguments.get("contact");

        // Validate mandatory parameters for specific actions
        if ("send_message".equalsIgnoreCase(action)) {
            if (contact == null || contact.isBlank()) {
                return new ToolResultDTO(false, "Argument 'contact' is required for 'send_message'.", Map.of());
            }
            String message = (String) arguments.get("message");
            if (message == null || message.isBlank()) {
                return new ToolResultDTO(false, "Argument 'message' is required for 'send_message'.", Map.of());
            }
            return executeSendMessageWrapper(contact, message);
        }

        if ("open_chat".equalsIgnoreCase(action)) {
            if (contact == null || contact.isBlank()) {
                return new ToolResultDTO(false, "Argument 'contact' is required for 'open_chat'.", Map.of());
            }
            return executeOpenChatWrapper(contact);
        }

        if ("read_chat".equalsIgnoreCase(action)) {
            int limit = arguments.containsKey("limit") ? ((Number) arguments.get("limit")).intValue() : 10;
            String expectedMessage = (String) arguments.get("expected_message");
            return executeReadChat(contact, limit, expectedMessage);
        }

        return new ToolResultDTO(false, "Unsupported WhatsApp action: " + action, Map.of());
    }

    private ToolResultDTO executeSendMessageWrapper(String contact, String message) {
        try {
            ensureWhatsAppReady();
            return executeSendMessage(contact, message);
        } catch (Exception e) {
            log.error("WhatsApp send_message failed", e);
            return new ToolResultDTO(false, "WhatsApp send_message failed: " + e.getMessage(), Map.of());
        }
    }

    private ToolResultDTO executeOpenChatWrapper(String contact) {
        try {
            ensureWhatsAppReady();
            return executeOpenChat(contact);
        } catch (Exception e) {
            log.error("WhatsApp open_chat failed", e);
            return new ToolResultDTO(false, "WhatsApp open_chat failed: " + e.getMessage(), Map.of());
        }
    }

    /**
     * Brings WhatsApp to foreground or starts it if needed.
     */
    private void ensureWhatsAppReady() throws Exception {
        ToolResultDTO focusResult = desktopAutomation.ensureFocus("WhatsApp");
        if (!focusResult.successful()) {
            log.info("WhatsApp window not in foreground, attempting protocol launch...");
            new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", "Start-Process 'whatsapp:'")
                .start()
                .waitFor();
            Thread.sleep(2000);
            focusResult = desktopAutomation.ensureFocus("WhatsApp");
            if (!focusResult.successful()) {
                throw new IllegalStateException("Failed to bring WhatsApp Desktop to foreground. Ensure WhatsApp is installed.");
            }
        }
    }

    /**
     * Executes the deterministic 6-step send_message sequence.
     */
    private ToolResultDTO executeSendMessage(String contact, String message) throws InterruptedException {
        log.info("Executing WhatsApp send_message to '{}'", contact);

        // 1. Focus search bar via native shortcut Ctrl+F
        desktopAutomation.pressKeyCombination("CTRL+F");
        Thread.sleep(250);

        // 2. Select search and enter contact name
        DesktopAutomationTool.setClipboardText(contact);
        desktopAutomation.pressKeyCombination("CTRL+V");
        Thread.sleep(350);

        // 3. Confirm contact selection with Enter
        desktopAutomation.pressKeyCombination("ENTER");
        Thread.sleep(500);

        // 4. Inject message content into clipboard and paste
        DesktopAutomationTool.setClipboardText(message);
        desktopAutomation.pressKeyCombination("CTRL+V");
        Thread.sleep(200);

        // 5. Dispatch message with Enter
        desktopAutomation.pressKeyCombination("ENTER");
        Thread.sleep(200);

        log.info("WhatsApp message successfully dispatched to '{}'", contact);
        return new ToolResultDTO(
            true,
            "Successfully sent WhatsApp message to '" + contact + "'. Content: \"" + message + "\"",
            Map.of("contact", contact, "status", "SENT", "action", "send_message")
        );
    }

    /**
     * Executes open_chat to focus a contact conversation without sending a message.
     */
    private ToolResultDTO executeOpenChat(String contact) throws InterruptedException {
        log.info("Executing WhatsApp open_chat for '{}'", contact);

        desktopAutomation.pressKeyCombination("CTRL+F");
        Thread.sleep(250);

        DesktopAutomationTool.setClipboardText(contact);
        desktopAutomation.pressKeyCombination("CTRL+V");
        Thread.sleep(350);

        desktopAutomation.pressKeyCombination("ENTER");
        Thread.sleep(400);

        return new ToolResultDTO(
            true,
            "Successfully opened WhatsApp chat with '" + contact + "'.",
            Map.of("contact", contact, "status", "OPENED", "action", "open_chat")
        );
    }

    /**
     * Extracts recent messages from the active conversation using Win32 UIA.
     * Optionally performs closed-loop verification against expectedMessage.
     */
    public ToolResultDTO executeReadChat(String contact, int limit, String expectedMessage) {
        log.info("Executing WhatsApp read_chat (contact={}, limit={}, expectedMessage={})", contact, limit, expectedMessage);

        try {
            ensureWhatsAppReady();

            if (contact != null && !contact.isBlank()) {
                executeOpenChat(contact);
                Thread.sleep(400);
            }

            String script = buildChatExtractionScript();
            String rawJson = desktopAutomation.runPowershell(script).trim();

            if (rawJson.startsWith("ERROR")) {
                return new ToolResultDTO(false, rawJson, Map.of());
            }

            List<Map<String, Object>> rawElements = List.of();
            if (!rawJson.isBlank() && !rawJson.equals("[]")) {
                try {
                    rawElements = objectMapper.readValue(rawJson, new TypeReference<>() {});
                } catch (Exception pe) {
                    log.warn("Failed to parse UIA JSON: {}, raw: {}", pe.getMessage(), rawJson);
                }
            }

            List<WhatsAppMessage> parsedMessages = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            for (Map<String, Object> el : rawElements) {
                String name = (String) el.get("name");
                if (name == null || name.isBlank()) continue;
                if (!seen.add(name)) continue; // avoid duplicates

                WhatsAppMessage msg = parseMessageElement(name, contact);
                parsedMessages.add(msg);
            }

            // Limit to most recent N messages
            int startIndex = Math.max(0, parsedMessages.size() - limit);
            List<WhatsAppMessage> recentMessages = parsedMessages.subList(startIndex, parsedMessages.size());

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("action", "read_chat");
            metadata.put("contact", contact != null ? contact : "active_chat");
            metadata.put("count", recentMessages.size());
            metadata.put("messages", recentMessages);

            // Closed-loop verification
            if (expectedMessage != null && !expectedMessage.isBlank()) {
                boolean verified = false;
                WhatsAppMessage matchedMessage = null;

                for (int i = recentMessages.size() - 1; i >= 0; i--) {
                    WhatsAppMessage m = recentMessages.get(i);
                    if (m.text().toLowerCase().contains(expectedMessage.toLowerCase())
                            || m.raw().toLowerCase().contains(expectedMessage.toLowerCase())) {
                        verified = true;
                        matchedMessage = m;
                        break;
                    }
                }

                metadata.put("verified", verified);
                metadata.put("expected_message", expectedMessage);

                if (verified) {
                    metadata.put("matched_message", matchedMessage);
                    String summary = String.format(
                        "VERIFICATION PASSED: Message \"%s\" verified in WhatsApp chat history! (Sender: %s, Time: %s, Status: %s)",
                        expectedMessage, matchedMessage.sender(), matchedMessage.timestamp(), matchedMessage.status()
                    );
                    return new ToolResultDTO(true, summary + "\n\nRecent messages:\n" + formatMessages(recentMessages), metadata);
                } else {
                    String summary = String.format(
                        "VERIFICATION FAILED: Expected message \"%s\" was NOT found in recent chat messages.",
                        expectedMessage
                    );
                    return new ToolResultDTO(false, summary + "\n\nRecent messages:\n" + formatMessages(recentMessages), metadata);
                }
            }

            String summary = String.format("Successfully read %d recent messages from WhatsApp chat:", recentMessages.size());
            return new ToolResultDTO(true, summary + "\n" + formatMessages(recentMessages), metadata);

        } catch (Exception e) {
            log.error("Failed to read WhatsApp chat", e);
            return new ToolResultDTO(false, "Failed to read WhatsApp chat: " + e.getMessage(), Map.of());
        }
    }

    /**
     * Parses raw accessible text into structured message records.
     */
    public WhatsAppMessage parseMessageElement(String rawName, String targetContact) {
        String sender = "Unknown";
        String timestamp = "";
        String status = "Received";

        // Extract timestamp
        Matcher timeMatcher = TIME_PATTERN.matcher(rawName);
        if (timeMatcher.find()) {
            timestamp = timeMatcher.group(1);
        }

        // Status keywords
        String lower = rawName.toLowerCase();
        if (lower.contains("read") || lower.contains("seen")) {
            status = "Read";
        } else if (lower.contains("delivered")) {
            status = "Delivered";
        } else if (lower.contains("sent")) {
            status = "Sent";
        } else if (lower.contains("pending")) {
            status = "Pending";
        }

        // Check if message is from 'You'
        if (rawName.startsWith("You,") || rawName.startsWith("You:") || rawName.startsWith("you,") || rawName.startsWith("you:")) {
            sender = "You";
            if ("Received".equals(status)) status = "Sent";
        } else if (targetContact != null && !targetContact.isBlank() && rawName.startsWith(targetContact)) {
            sender = targetContact;
        }

        String[] parts = rawName.split(",");
        if (parts.length >= 2) {
            sender = parts[0].trim();
            List<String> contentParts = new ArrayList<>();
            for (int i = 1; i < parts.length; i++) {
                String p = parts[i].trim();
                if (p.isBlank()) continue;
                if (p.equalsIgnoreCase("Sent") || p.equalsIgnoreCase("Delivered")
                        || p.equalsIgnoreCase("Read") || p.equalsIgnoreCase("Pending")
                        || p.equalsIgnoreCase("Seen")) {
                    continue;
                }
                if (TIME_PATTERN.matcher(p).matches()) {
                    if (timestamp.isBlank()) timestamp = p;
                    continue;
                }
                contentParts.add(p);
            }
            String text = String.join(", ", contentParts);
            if (text.isBlank()) text = rawName;
            return new WhatsAppMessage(sender, text, timestamp, status, rawName);
        }

        return new WhatsAppMessage(sender, rawName, timestamp, status, rawName);
    }

    private String formatMessages(List<WhatsAppMessage> messages) {
        if (messages.isEmpty()) {
            return "(No messages found in active conversation)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            WhatsAppMessage m = messages.get(i);
            sb.append(String.format("%d. [%s] %s: %s",
                i + 1,
                m.timestamp().isBlank() ? "Recent" : m.timestamp(),
                m.sender(),
                m.text()
            ));
            if ("You".equalsIgnoreCase(m.sender())) {
                sb.append(String.format(" (%s)", m.status()));
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String buildChatExtractionScript() {
        return String.join("\n",
            "Add-Type -AssemblyName UIAutomationClient",
            "Add-Type -AssemblyName UIAutomationTypes",
            "Add-Type @'",
            "using System; using System.Runtime.InteropServices; using System.Text;",
            "public class WAChat {",
            "  [DllImport(\"user32.dll\")] public static extern bool SetForegroundWindow(IntPtr hWnd);",
            "  [DllImport(\"user32.dll\")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);",
            "  public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);",
            "  [DllImport(\"user32.dll\")] public static extern bool EnumWindows(EnumWindowsProc cb, IntPtr lParam);",
            "  [DllImport(\"user32.dll\")] public static extern int GetWindowText(IntPtr h, StringBuilder t, int c);",
            "  [DllImport(\"user32.dll\")] public static extern bool IsWindowVisible(IntPtr h);",
            "}",
            "'@",
            "$waHwnd = [IntPtr]::Zero",
            "[WAChat]::EnumWindows({param($h,$l)",
            "  if (-not [WAChat]::IsWindowVisible($h)) { return $true }",
            "  $t = New-Object Text.StringBuilder 256",
            "[WAChat]::GetWindowText($h, $t, 256) | Out-Null",
            "  if ($t.ToString() -like '*WhatsApp*') {",
            "    $script:waHwnd = $h",
            "    return $false",
            "  }",
            "  return $true",
            "}, [IntPtr]::Zero) | Out-Null",
            "",
            "if ($waHwnd -eq [IntPtr]::Zero) {",
            "  Write-Output 'ERROR: WhatsApp window not found'",
            "  exit 0",
            "}",
            "",
            "$el = [System.Windows.Automation.AutomationElement]::FromHandle($waHwnd)",
            "$all = $el.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)",
            "",
            "$ignoreList = @(",
            "  'WhatsApp', 'System', 'Close', 'Minimize', 'Maximize', 'Restore',",
            "  'Search', 'Chats', 'Status', 'Calls', 'Settings', 'Profile',",
            "  'Type a message', 'Attach', 'Record voice message', 'Video call',",
            "  'Voice call', 'More options', 'Back', 'Filter chats by: all',",
            "  'Online', 'offline', 'typing...', 'last seen', 'Mute notifications',",
            "  'Disappearing messages', 'Lock chat', 'Search with Google', 'Copy'",
            ")",
            "",
            "$items = New-Object System.Collections.ArrayList",
            "",
            "foreach ($item in $all) {",
            "  $ct = $item.Current.ControlType.ProgrammaticName",
            "  $aid = $item.Current.AutomationId",
            "  $name = $item.Current.Name",
            "",
            "  if (-not $name -or -not $name.Trim()) { continue }",
            "  $trimmed = $name.Trim()",
            "  if ($ignoreList -contains $trimmed) { continue }",
            "  if ($ct -in @('ControlType.TitleBar', 'ControlType.MenuBar', 'ControlType.Menu', 'ControlType.ScrollBar', 'ControlType.Thumb')) { continue }",
            "",
            "  $isTime = $trimmed -match '\\b\\d{1,2}:\\d{2}\\b'",
            "  $isYou = $trimmed -match '^(You|you)[,:]'",
            "  $isListItem = ($ct -eq 'ControlType.ListItem' -or $ct -eq 'ControlType.Group')",
            "",
            "  if ($isTime -or $isYou -or $isListItem) {",
            "    $items.Add([PSCustomObject]@{",
            "      id = $aid",
            "      control = $ct",
            "      name = $trimmed",
            "    }) | Out-Null",
            "  }",
            "}",
            "",
            "if ($items.Count -gt 0) {",
            "  $items | ConvertTo-Json -Compress",
            "} else {",
            "  Write-Output '[]'",
            "}"
        );
    }
}
