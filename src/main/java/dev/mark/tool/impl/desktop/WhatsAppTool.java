package dev.mark.tool.impl.desktop;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
 * search-sanitized navigation, Windows system clipboard injection,
 * geometric pane separation (Sidebar vs Conversation), and
 * accessibility-based chat reading and verification.
 */
@Component
public class WhatsAppTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppTool.class);
    private static final Pattern TIME_PATTERN = Pattern.compile("\\b(\\d{1,2}:\\d{2}\\s*(?:AM|PM|am|pm)?)\\b");
    private static final Pattern UNREAD_PATTERN = Pattern.compile("(?i)\\b(\\d+)\\s*unread\\b");

    private final DesktopAutomationTool desktopAutomation;
    private final ObjectMapper objectMapper;

    public WhatsAppTool(DesktopAutomationTool desktopAutomation, ObjectMapper objectMapper) {
        this.desktopAutomation = desktopAutomation;
        this.objectMapper = objectMapper;
    }

    public record WhatsAppMessage(boolean fromMe, String sender, String text, String timestamp, String status, String raw) {}
    public record WhatsAppUnreadChat(String contact, int unreadCount, String lastSnippet, String timestamp, String raw) {}

    @Override
    public String name() {
        return "whatsapp_automation";
    }

    @Override
    public String description() {
        return "Deterministic automation skill for WhatsApp Desktop on Windows. "
             + "Actions supported:\n"
             + "1. 'send_message' (send text/emojis to a contact. Requires: 'contact', 'message')\n"
             + "2. 'open_chat' (focus a conversation without sending. Requires: 'contact')\n"
             + "3. 'read_chat' (read recent messages from the active conversation pane. Optional: 'contact', 'limit')\n"
             + "4. 'list_unread_chats' (lists only chats that currently have pending unread messages. Optional: 'limit')\n"
             + "5. 'verify_message' (READ-ONLY verifier. Checks if a message exists in the conversation history without sending anything. Requires: 'contact', 'expected_message').";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "action", Map.of(
                    "type", "string",
                    "description", "WhatsApp action: 'send_message', 'open_chat', 'read_chat', 'list_unread_chats', or 'verify_message'"
                ),
                "contact", Map.of(
                    "type", "string",
                    "description", "Contact name or phone number (required for send_message, open_chat, verify_message; optional for read_chat)"
                ),
                "message", Map.of(
                    "type", "string",
                    "description", "Text content to send (required for 'send_message')"
                ),
                "expected_message", Map.of(
                    "type", "string",
                    "description", "Text content to search/verify in conversation history (required for 'verify_message', optional for 'read_chat')"
                ),
                "limit", Map.of(
                    "type", "integer",
                    "description", "Number of items to return (default: 10)"
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
        int limit = arguments.containsKey("limit") ? ((Number) arguments.get("limit")).intValue() : 10;

        try {
            switch (action.toLowerCase()) {
                case "send_message":
                    if (contact == null || contact.isBlank()) {
                        return new ToolResultDTO(false, "Argument 'contact' is required for 'send_message'.", Map.of());
                    }
                    String message = (String) arguments.get("message");
                    if (message == null || message.isBlank()) {
                        return new ToolResultDTO(false, "Argument 'message' is required for 'send_message'.", Map.of());
                    }
                    ensureWhatsAppReady();
                    return executeSendMessage(contact, message);

                case "open_chat":
                    if (contact == null || contact.isBlank()) {
                        return new ToolResultDTO(false, "Argument 'contact' is required for 'open_chat'.", Map.of());
                    }
                    ensureWhatsAppReady();
                    return executeOpenChat(contact);

                case "list_unread_chats":
                    ensureWhatsAppReady();
                    return executeListUnreadChats(limit);

                case "read_chat":
                    ensureWhatsAppReady();
                    String expectedMessage = (String) arguments.get("expected_message");
                    return executeReadChat(contact, limit, expectedMessage);

                case "verify_message":
                    if (contact == null || contact.isBlank()) {
                        return new ToolResultDTO(false, "Argument 'contact' is required for 'verify_message'.", Map.of());
                    }
                    String expected = (String) arguments.get("expected_message");
                    if (expected == null || expected.isBlank()) {
                        return new ToolResultDTO(false, "Argument 'expected_message' is required for 'verify_message'.", Map.of());
                    }
                    ensureWhatsAppReady();
                    return executeVerifyMessage(contact, expected, limit);

                default:
                    return new ToolResultDTO(false, "Unsupported WhatsApp action: " + action, Map.of());
            }

        } catch (Exception e) {
            log.error("WhatsApp action '{}' failed", action, e);
            return new ToolResultDTO(false, "WhatsApp action failed: " + e.getMessage(), Map.of());
        }
    }

    /**
     * Brings WhatsApp to foreground or launches it if needed.
     */
    private void ensureWhatsAppReady() throws Exception {
        ToolResultDTO focusResult = desktopAutomation.ensureFocus("WhatsApp");
        if (!focusResult.successful()) {
            log.info("WhatsApp window not in foreground, attempting protocol launch...");
            launchWhatsAppProtocol("whatsapp:");
            Thread.sleep(2000);
            focusResult = desktopAutomation.ensureFocus("WhatsApp");
            if (!focusResult.successful()) {
                throw new IllegalStateException("Failed to bring WhatsApp Desktop to foreground. Ensure WhatsApp is installed.");
            }
        }
        // Win32 SW_MAXIMIZE already maximizes to full width and height without triggering Windows 11 Snap
        Thread.sleep(150);
    }

    void launchWhatsAppProtocol(String uri) {
        try {
            new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", "Start-Process '" + uri + "'")
                .start()
                .waitFor();
        } catch (Exception e) {
            log.warn("Failed to launch WhatsApp protocol '{}': {}", uri, e.getMessage());
        }
    }

    /**
     * Sanitizes search input by selecting all existing text and backspacing before pasting.
     */
    private void searchAndSelectContact(String contact) throws InterruptedException {
        // 1. Focus search bar via native shortcut Ctrl+F
        desktopAutomation.pressKeyCombination("CTRL+F");
        Thread.sleep(200);

        // 2. Clear any residual text from previous searches (Ctrl+A -> Backspace)
        desktopAutomation.pressKeyCombination("CTRL+A");
        Thread.sleep(100);
        desktopAutomation.pressKeyCombination("BACKSPACE");
        Thread.sleep(100);

        // 3. Paste clean contact query
        DesktopAutomationTool.setClipboardText(contact);
        desktopAutomation.pressKeyCombination("CTRL+V");
        Thread.sleep(500);

        // 4. Move selection from search box down to first search result
        desktopAutomation.pressKeyCombination("DOWN");
        Thread.sleep(150);

        // 5. Confirm selection with Enter and wait for conversation pane to settle
        desktopAutomation.pressKeyCombination("ENTER");
        Thread.sleep(800);
    }

    /**
     * Executes the deterministic send_message sequence.
     */
    private ToolResultDTO executeSendMessage(String contact, String message) throws Exception {
        log.info("Executing WhatsApp send_message to '{}'", contact);

        executeOpenChat(contact);
        Thread.sleep(300);

        // Inject message content into clipboard and paste
        DesktopAutomationTool.setClipboardText(message);
        desktopAutomation.pressKeyCombination("CTRL+V");
        Thread.sleep(200);

        // Dispatch message with Enter
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
     * Uses native Windows WhatsApp protocol for phone numbers, and UI search for names/groups.
     */
    private ToolResultDTO executeOpenChat(String contact) throws Exception {
        log.info("Executing WhatsApp open_chat for '{}'", contact);

        if (contact == null || contact.isBlank()) {
            return new ToolResultDTO(false, "Contact is required for 'open_chat'", Map.of());
        }

        String digits = contact.replaceAll("[^0-9]", "");
        // If contact is a phone number (e.g. 8280901077 or +91 82809 01077, 7+ digits)
        if (digits.length() >= 7 && contact.matches("^[\\+]?[0-9\\s\\-\\(\\)]{7,}$")) {
            log.info("Contact '{}' detected as phone number ({} digits), using native WhatsApp protocol...", contact, digits);
            launchWhatsAppProtocol("whatsapp://send?phone=" + digits);
            Thread.sleep(1200);
            desktopAutomation.ensureFocus("WhatsApp");
            Thread.sleep(300);
        } else {
            searchAndSelectContact(contact);
        }

        return new ToolResultDTO(
            true,
            "Successfully opened WhatsApp chat with '" + contact + "'.",
            Map.of("contact", contact, "status", "OPENED", "action", "open_chat")
        );
    }

    /**
     * Lists ONLY chats in the sidebar that have pending unread message badges.
     */
    public ToolResultDTO executeListUnreadChats(int limit) {
        log.info("Executing WhatsApp list_unread_chats (limit={})", limit);

        try {
            String script = buildSidebarUnreadExtractionScript();
            String rawJson = desktopAutomation.runPowershell(script).trim();

            if (rawJson.startsWith("ERROR")) {
                return new ToolResultDTO(false, rawJson, Map.of());
            }

            List<Map<String, Object>> rawElements = parseJsonElements(rawJson);

            List<WhatsAppUnreadChat> unreadChats = new ArrayList<>();
            Set<String> seenContacts = new HashSet<>();

            for (Map<String, Object> el : rawElements) {
                String name = (String) el.get("name");
                if (name == null || name.isBlank()) continue;

                WhatsAppUnreadChat chat = parseUnreadChatElement(name);
                if (chat != null && seenContacts.add(chat.contact().toLowerCase())) {
                    unreadChats.add(chat);
                    if (unreadChats.size() >= limit) break;
                }
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("action", "list_unread_chats");
            metadata.put("unread_chats_count", unreadChats.size());
            metadata.put("unread_chats", unreadChats);

            if (unreadChats.isEmpty()) {
                String observation = "No unread messages found in WhatsApp. All chats are currently caught up.";
                return new ToolResultDTO(true, observation, metadata);
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Found %d chat(s) with unread messages in WhatsApp:\n", unreadChats.size()));
            for (int i = 0; i < unreadChats.size(); i++) {
                WhatsAppUnreadChat uc = unreadChats.get(i);
                sb.append(String.format("%d. %s (%d unread message%s) [%s]: \"%s\"\n",
                    i + 1,
                    uc.contact(),
                    uc.unreadCount(),
                    uc.unreadCount() == 1 ? "" : "s",
                    uc.timestamp().isBlank() ? "Recent" : uc.timestamp(),
                    uc.lastSnippet()
                ));
            }

            return new ToolResultDTO(true, sb.toString().trim(), metadata);

        } catch (Exception e) {
            log.error("Failed to list unread WhatsApp chats", e);
            return new ToolResultDTO(false, "Failed to list unread WhatsApp chats: " + e.getMessage(), Map.of());
        }
    }

    /**
     * Extracts recent messages strictly from the conversation pane.
     */
    public ToolResultDTO executeReadChat(String contact, int limit, String expectedMessage) {
        log.info("Executing WhatsApp read_chat (contact={}, limit={}, expectedMessage={})", contact, limit, expectedMessage);

        try {
            if (contact != null && !contact.isBlank()) {
                executeOpenChat(contact);
                Thread.sleep(750);
            }

            String script = buildConversationPaneExtractionScript();
            String rawJson = desktopAutomation.runPowershell(script).trim();

            if (rawJson.startsWith("ERROR")) {
                return new ToolResultDTO(false, rawJson, Map.of());
            }

            List<Map<String, Object>> rawElements = parseJsonElements(rawJson);

            List<WhatsAppMessage> parsedMessages = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            for (Map<String, Object> el : rawElements) {
                String name = (String) el.get("name");
                if (name == null || name.isBlank()) continue;
                if (!seen.add(name)) continue;

                WhatsAppMessage msg = parseMessageElement(name, contact);
                parsedMessages.add(msg);
            }

            // Limit to most recent N messages
            int startIndex = Math.max(0, parsedMessages.size() - limit);
            List<WhatsAppMessage> recentMessages = parsedMessages.subList(startIndex, parsedMessages.size());

            WhatsAppMessage lastMessage = recentMessages.isEmpty() ? null : recentMessages.get(recentMessages.size() - 1);
            WhatsAppMessage lastFromMe = null;
            WhatsAppMessage lastFromThem = null;
            for (int i = recentMessages.size() - 1; i >= 0; i--) {
                WhatsAppMessage m = recentMessages.get(i);
                if (m.fromMe() && lastFromMe == null) {
                    lastFromMe = m;
                } else if (!m.fromMe() && lastFromThem == null) {
                    lastFromThem = m;
                }
                if (lastFromMe != null && lastFromThem != null) break;
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("action", "read_chat");
            metadata.put("contact", contact != null ? contact : "active_chat");
            metadata.put("count", recentMessages.size());
            metadata.put("messages", recentMessages);
            if (lastMessage != null) metadata.put("last_message", lastMessage);
            if (lastFromMe != null) metadata.put("last_from_me", lastFromMe);
            if (lastFromThem != null) metadata.put("last_from_them", lastFromThem);

            if (expectedMessage != null && !expectedMessage.isBlank()) {
                return evaluateVerification(recentMessages, expectedMessage, contact, metadata);
            }

            StringBuilder summary = new StringBuilder();
            summary.append(String.format("Successfully read %d recent message(s) from conversation history:\n", recentMessages.size()));
            if (lastFromMe != null) {
                summary.append(String.format("- Last message from my side: \"%s\" [%s, Status: %s]\n", lastFromMe.text(), lastFromMe.timestamp(), lastFromMe.status()));
            }
            if (lastFromThem != null) {
                summary.append(String.format("- Last message from their side: \"%s\" [%s, by %s]\n", lastFromThem.text(), lastFromThem.timestamp(), lastFromThem.sender()));
            }
            summary.append("\nAll messages:\n").append(formatMessages(recentMessages));
            return new ToolResultDTO(true, summary.toString().trim(), metadata);

        } catch (Exception e) {
            log.error("Failed to read WhatsApp conversation", e);
            return new ToolResultDTO(false, "Failed to read WhatsApp conversation: " + e.getMessage(), Map.of());
        }
    }

    /**
     * Strict read-only verification: checks if expectedMessage exists in the chat history.
     * Absolutely never types or re-sends messages.
     */
    private ToolResultDTO executeVerifyMessage(String contact, String expectedMessage, int limit) {
        log.info("Executing READ-ONLY verify_message for contact='{}', expected='{}'", contact, expectedMessage);
        return executeReadChat(contact, limit, expectedMessage);
    }

    private ToolResultDTO evaluateVerification(List<WhatsAppMessage> recentMessages, String expectedMessage, String contact, Map<String, Object> metadata) {
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
            String sideStr = matchedMessage.fromMe() ? "My Side" : "Their Side";
            String summary = String.format(
                "VERIFICATION PASSED: Confirmed message \"%s\" exists in WhatsApp chat history with '%s'! [Side: %s, Sender: %s, Time: %s, Status: %s]",
                expectedMessage, contact != null ? contact : "active_chat", sideStr, matchedMessage.sender(), matchedMessage.timestamp(), matchedMessage.status()
            );
            return new ToolResultDTO(true, summary + "\n\nRecent conversation messages:\n" + formatMessages(recentMessages), metadata);
        } else {
            String summary = String.format(
                "VERIFICATION FAILED: Message \"%s\" was NOT found in recent chat history with '%s'. (READ-ONLY check - no messages were sent)",
                expectedMessage, contact != null ? contact : "active_chat"
            );
            return new ToolResultDTO(false, summary + "\n\nRecent conversation messages:\n" + formatMessages(recentMessages), metadata);
        }
    }

    /**
     * Safely deserializes UIA JSON string into a list of elements.
     * Supports both JSON arrays [...] and single JSON objects {...} (which PowerShell outputs when count == 1).
     */
    List<Map<String, Object>> parseJsonElements(String rawJson) {
        if (rawJson == null || rawJson.isBlank() || rawJson.equals("[]")) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            List<Map<String, Object>> list = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode n : root) {
                    list.add(objectMapper.convertValue(n, new TypeReference<>() {}));
                }
            } else if (root.isObject()) {
                list.add(objectMapper.convertValue(root, new TypeReference<>() {}));
            }
            return list;
        } catch (Exception e) {
            log.warn("Failed to parse JSON elements: {}, raw: {}", e.getMessage(), rawJson);
            return List.of();
        }
    }

    /**
     * Parses an unread chat element from the sidebar.
     * Example input: "Himabindu Infosys, Okay, 1:49 AM, 2 unread messages"
     */
    public WhatsAppUnreadChat parseUnreadChatElement(String rawName) {
        Matcher unreadMatcher = UNREAD_PATTERN.matcher(rawName);
        if (!unreadMatcher.find() && !rawName.toLowerCase().contains("unread")) {
            return null; // Not an unread chat
        }

        int count = 1;
        if (unreadMatcher.find(0)) {
            try {
                count = Integer.parseInt(unreadMatcher.group(1));
            } catch (NumberFormatException ignored) {}
        }

        String contact = "Unknown";
        String lastSnippet = "";
        String timestamp = "";

        Matcher timeMatcher = TIME_PATTERN.matcher(rawName);
        if (timeMatcher.find()) {
            timestamp = timeMatcher.group(1);
        }

        String[] parts = rawName.split(",");
        if (parts.length >= 2) {
            contact = parts[0].trim();
            List<String> snippetParts = new ArrayList<>();
            for (int i = 1; i < parts.length; i++) {
                String p = parts[i].trim();
                if (p.isBlank()) continue;
                if (UNREAD_PATTERN.matcher(p).find() || p.toLowerCase().contains("unread")) continue;
                if (TIME_PATTERN.matcher(p).matches()) continue;
                snippetParts.add(p);
            }
            lastSnippet = String.join(", ", snippetParts);
        } else {
            contact = rawName.trim();
        }

        return new WhatsAppUnreadChat(contact, count, lastSnippet, timestamp, rawName);
    }

    /**
     * Parses raw accessible message text into structured message records.
     * Identifies whether the message is from your side (You / Outgoing) or their side (Incoming / Group Member).
     */
    public WhatsAppMessage parseMessageElement(String rawName, String targetContact) {
        String sender = "Unknown";
        String timestamp = "";
        String status = "Received";

        Matcher timeMatcher = TIME_PATTERN.matcher(rawName);
        if (timeMatcher.find()) {
            timestamp = timeMatcher.group(1);
        }

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

        boolean fromMe = lower.startsWith("you,") || lower.startsWith("you:");
        if (fromMe) {
            sender = "You";
            if ("Received".equals(status)) status = "Sent";
        } else if (targetContact != null && !targetContact.isBlank() && rawName.startsWith(targetContact)) {
            sender = targetContact;
        }

        String[] parts = rawName.split(",");
        if (parts.length >= 2) {
            if (!fromMe) {
                sender = parts[0].trim();
            }
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
            return new WhatsAppMessage(fromMe, sender, text, timestamp, status, rawName);
        }

        return new WhatsAppMessage(fromMe, sender, rawName, timestamp, status, rawName);
    }

    private String formatMessages(List<WhatsAppMessage> messages) {
        if (messages.isEmpty()) {
            return "(No conversation messages found in active pane)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            WhatsAppMessage m = messages.get(i);
            String side = m.fromMe() ? "[My Side]" : "[Their Side]";
            sb.append(String.format("%d. %s [%s] %s: %s",
                i + 1,
                side,
                m.timestamp().isBlank() ? "Recent" : m.timestamp(),
                m.sender(),
                m.text()
            ));
            if (m.fromMe()) {
                sb.append(String.format(" (Status: %s)", m.status()));
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Extracts elements specifically from the left sidebar where unread chats reside.
     */
    private String buildSidebarUnreadExtractionScript() {
        return String.join("\n",
            "Add-Type -AssemblyName UIAutomationClient",
            "Add-Type -AssemblyName UIAutomationTypes",
            "Add-Type @'",
            "using System; using System.Runtime.InteropServices; using System.Text;",
            "public class WAUnreadUIA {",
            "  [DllImport(\"user32.dll\")] public static extern bool EnumWindows(EnumWindowsProc cb, IntPtr lParam);",
            "  public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);",
            "  [DllImport(\"user32.dll\")] public static extern int GetWindowText(IntPtr h, StringBuilder t, int c);",
            "  [DllImport(\"user32.dll\")] public static extern int GetClassName(IntPtr h, StringBuilder t, int c);",
            "  [DllImport(\"user32.dll\")] public static extern bool IsWindowVisible(IntPtr h);",
            "  [DllImport(\"user32.dll\")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);",
            "}",
            "'@",
            "$waHwnd = [IntPtr]::Zero",
            "[WAUnreadUIA]::EnumWindows({param($h,$l)",
            "  if (-not [WAUnreadUIA]::IsWindowVisible($h)) { return $true }",
            "  $c = New-Object Text.StringBuilder 256",
            "  [WAUnreadUIA]::GetClassName($h, $c, 256) | Out-Null",
            "  $cls = $c.ToString()",
            "  if ($cls -like '*Chrome*') { return $true }",
            "",
            "  $t = New-Object Text.StringBuilder 256",
            "  [WAUnreadUIA]::GetWindowText($h, $t, 256) | Out-Null",
            "  $title = $t.ToString()",
            "",
            "  if ($cls -eq 'WinUIDesktopWin32WindowClass' -or $title -like '*WhatsApp*') {",
            "    $procId = 0",
            "    [WAUnreadUIA]::GetWindowThreadProcessId($h, [ref]$procId) | Out-Null",
            "    if ($procId -gt 0) {",
            "      $p = Get-Process -Id $procId -ErrorAction SilentlyContinue",
            "      if ($p -and $p.ProcessName -notmatch '(?i)chrome') {",
            "        $script:waHwnd = $h",
            "        return $false",
            "      }",
            "    }",
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
            "$winRect = $el.Current.BoundingRectangle",
            "$splitX = $winRect.X + [math]::Min(420, $winRect.Width * 0.38)",
            "",
            "$all = $el.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)",
            "$items = New-Object System.Collections.ArrayList",
            "",
            "foreach ($item in $all) {",
            "  $name = $item.Current.Name",
            "  if (-not $name -or -not $name.Trim()) { continue }",
            "  $trimmed = $name.Trim()",
            "",
            "  # Geometric filter: only inspect elements inside the left sidebar",
            "  $rect = $item.Current.BoundingRectangle",
            "  if ($rect.IsEmpty -or $rect.X -ge $splitX) { continue }",
            "",
            "  # Check for unread indicators in the name or automation ID",
            "  if ($trimmed -match '(?i)\\b(\\d+)\\s*unread\\b' -or $trimmed -match '(?i)\\bunread\\s*message' -or ($item.Current.AutomationId -and $item.Current.AutomationId -like '*unread*')) {",
            "    $items.Add([PSCustomObject]@{",
            "      id = $item.Current.AutomationId",
            "      name = $trimmed",
            "    }) | Out-Null",
            "  }",
            "}",
            "",
            "if ($items.Count -gt 0) {",
            "  $json = $items | ConvertTo-Json -Compress",
            "  if ($json.StartsWith('{')) { \"[$json]\" } else { $json }",
            "} else {",
            "  Write-Output '[]'",
            "}"
        );
    }

    /**
     * Extracts elements strictly from the right-hand conversation message pane.
     * Excludes the left sidebar by geometric bounding coordinate check.
     */
    private String buildConversationPaneExtractionScript() {
        return String.join("\n",
            "Add-Type -AssemblyName UIAutomationClient",
            "Add-Type -AssemblyName UIAutomationTypes",
            "Add-Type @'",
            "using System; using System.Runtime.InteropServices; using System.Text;",
            "public class WAPaneUIA {",
            "  [DllImport(\"user32.dll\")] public static extern bool EnumWindows(EnumWindowsProc cb, IntPtr lParam);",
            "  public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);",
            "  [DllImport(\"user32.dll\")] public static extern int GetWindowText(IntPtr h, StringBuilder t, int c);",
            "  [DllImport(\"user32.dll\")] public static extern int GetClassName(IntPtr h, StringBuilder t, int c);",
            "  [DllImport(\"user32.dll\")] public static extern bool IsWindowVisible(IntPtr h);",
            "  [DllImport(\"user32.dll\")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);",
            "}",
            "'@",
            "$waHwnd = [IntPtr]::Zero",
            "[WAPaneUIA]::EnumWindows({param($h,$l)",
            "  if (-not [WAPaneUIA]::IsWindowVisible($h)) { return $true }",
            "  $c = New-Object Text.StringBuilder 256",
            "  [WAPaneUIA]::GetClassName($h, $c, 256) | Out-Null",
            "  $cls = $c.ToString()",
            "  if ($cls -like '*Chrome*') { return $true }",
            "",
            "  $t = New-Object Text.StringBuilder 256",
            "  [WAPaneUIA]::GetWindowText($h, $t, 256) | Out-Null",
            "  $title = $t.ToString()",
            "",
            "  if ($cls -eq 'WinUIDesktopWin32WindowClass' -or $title -like '*WhatsApp*') {",
            "    $procId = 0",
            "    [WAPaneUIA]::GetWindowThreadProcessId($h, [ref]$procId) | Out-Null",
            "    if ($procId -gt 0) {",
            "      $p = Get-Process -Id $procId -ErrorAction SilentlyContinue",
            "      if ($p -and $p.ProcessName -notmatch '(?i)chrome') {",
            "        $script:waHwnd = $h",
            "        return $false",
            "      }",
            "    }",
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
            "$winRect = $el.Current.BoundingRectangle",
            "$splitX = $winRect.X + [math]::Min(420, $winRect.Width * 0.38)",
            "",
            "$all = $el.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)",
            "$items = New-Object System.Collections.ArrayList",
            "",
            "$ignoreList = @(",
            "  'WhatsApp', 'System', 'Close', 'Minimize', 'Maximize', 'Restore',",
            "  'Search', 'Chats', 'Status', 'Calls', 'Settings', 'Profile',",
            "  'Type a message', 'Attach', 'Record voice message', 'Video call',",
            "  'Voice call', 'More options', 'Back', 'Filter chats by: all',",
            "  'Online', 'offline', 'typing...', 'last seen', 'Mute notifications',",
            "  'Disappearing messages', 'Lock chat', 'Search with Google', 'Copy',",
            "  'Forward', 'Star', 'Delete', 'Reply', 'Info', 'React', 'Add reaction',",
            "  'Emoji', 'Sticker', 'GIF', 'Voice message', 'Document', 'Photos and videos',",
            "  'Camera', 'Contact', 'Poll'",
            ")",
            "",
            "foreach ($item in $all) {",
            "  $ct = $item.Current.ControlType.ProgrammaticName",
            "  $aid = $item.Current.AutomationId",
            "  $name = $item.Current.Name",
            "",
            "  if (-not $name -or -not $name.Trim()) { continue }",
            "  $trimmed = $name.Trim()",
            "  if ($ignoreList -contains $trimmed) { continue }",
            "  if ($ct -in @('ControlType.TitleBar', 'ControlType.MenuBar', 'ControlType.Menu', 'ControlType.ScrollBar', 'ControlType.Thumb', 'ControlType.Separator', 'ControlType.SemanticZoom')) { continue }",
            "",
            "  # Do not drop elements if rect is empty (WinUI 3 virtualized text blocks report IsEmpty=true)",
            "  # Only filter out elements if bounding rectangle is explicitly known and inside left sidebar",
            "  $rect = $item.Current.BoundingRectangle",
            "  if (-not $rect.IsEmpty -and $rect.Width -gt 0 -and $rect.X -lt $splitX) { continue }",
            "",
            "  # Filter out standalone delivery indicators",
            "  if ($trimmed -in @('Sent', 'Delivered', 'Read', 'Pending', 'Seen')) { continue }",
            "",
            "  $items.Add([PSCustomObject]@{",
            "    id = $aid",
            "    control = $ct",
            "    name = $trimmed",
            "  }) | Out-Null",
            "}",
            "",
            "if ($items.Count -gt 0) {",
            "  $json = $items | ConvertTo-Json -Compress",
            "  if ($json.StartsWith('{')) { \"[$json]\" } else { $json }",
            "} else {",
            "  Write-Output '[]'",
            "}"
        );
    }
}
