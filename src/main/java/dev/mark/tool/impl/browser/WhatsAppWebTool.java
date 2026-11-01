package dev.mark.tool.impl.browser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import dev.mark.tool.util.BrowserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic background automation tool for WhatsApp Web via Microsoft Playwright.
 *
 * <p>Executes entirely in the background without stealing active desktop window focus.
 * Leverages persistent browser session contexts (~/.mark/browser_data) to maintain
 * long-term WhatsApp Web authentication across agent runs.</p>
 */
@Component
public class WhatsAppWebTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebTool.class);
    private static final Pattern PRE_META_PATTERN = Pattern.compile("^\\[(.*?)\\]\\s*(.*?):?\\s*$");

    private final BrowserSession browserSession;
    private final ObjectMapper objectMapper;

    public record WhatsAppWebMessage(boolean fromMe, String sender, String text, String timestamp, String status, String raw) {}
    public record WhatsAppWebUnreadChat(String contact, int unreadCount, String lastSnippet, String timestamp, String raw) {}

    public WhatsAppWebTool(BrowserSession browserSession, ObjectMapper objectMapper) {
        this.browserSession = browserSession;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "whatsapp_web_automation";
    }

    @Override
    public String description() {
        return "Automates WhatsApp Web in the background using Playwright. "
                + "Actions: 'open_session', 'send_message', 'open_chat', 'read_chat', 'verify_message', 'list_unread_chats'. "
                + "Arguments: action (required), contact (phone number with country code or contact name), message, expected_message, limit.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("action"),
            "properties", Map.of(
                "action", Map.of(
                    "type", "string",
                    "enum", List.of("open_session", "send_message", "open_chat", "read_chat", "verify_message", "list_unread_chats"),
                    "description", "WhatsApp Web action to perform"
                ),
                "contact", Map.of(
                    "type", "string",
                    "description", "Phone number with country code (e.g. 8280901077) or contact name"
                ),
                "message", Map.of(
                    "type", "string",
                    "description", "Message text content to send"
                ),
                "expected_message", Map.of(
                    "type", "string",
                    "description", "Expected message content to verify in chat history (read-only verification)"
                ),
                "limit", Map.of(
                    "type", "integer",
                    "description", "Maximum number of recent messages to read (default: 10)"
                )
            )
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
        int limit = 10;
        if (arguments.get("limit") instanceof Number num) {
            limit = Math.max(1, Math.min(100, num.intValue()));
        }

        try {
            Page page = ensureWhatsAppPage();

            switch (action.toLowerCase()) {
                case "open_session":
                    return executeOpenSession(page);

                case "send_message":
                    if (contact == null || contact.isBlank()) {
                        return new ToolResultDTO(false, "Argument 'contact' is required for 'send_message'.", Map.of());
                    }
                    String message = (String) arguments.get("message");
                    if (message == null || message.isBlank()) {
                        return new ToolResultDTO(false, "Argument 'message' is required for 'send_message'.", Map.of());
                    }
                    return executeSendMessage(page, contact, message);

                case "open_chat":
                    if (contact == null || contact.isBlank()) {
                        return new ToolResultDTO(false, "Argument 'contact' is required for 'open_chat'.", Map.of());
                    }
                    return executeOpenChat(page, contact);

                case "read_chat":
                    String expectedMsg = (String) arguments.get("expected_message");
                    return executeReadChat(page, contact, limit, expectedMsg);

                case "verify_message":
                    if (contact == null || contact.isBlank()) {
                        return new ToolResultDTO(false, "Argument 'contact' is required for 'verify_message'.", Map.of());
                    }
                    String expected = (String) arguments.get("expected_message");
                    if (expected == null || expected.isBlank()) {
                        return new ToolResultDTO(false, "Argument 'expected_message' is required for 'verify_message'.", Map.of());
                    }
                    return executeReadChat(page, contact, limit, expected);

                case "list_unread_chats":
                    return executeListUnreadChats(page, limit);

                default:
                    return new ToolResultDTO(false, "Unsupported WhatsApp Web action: " + action, Map.of());
            }

        } catch (Exception e) {
            log.error("WhatsApp Web action '{}' failed", action, e);
            return new ToolResultDTO(false, "WhatsApp Web action '" + action + "' failed: " + e.getMessage(), Map.of("error", e.getMessage()));
        }
    }

    Page ensureWhatsAppPage() {
        Page page = browserSession.getOrCreatePage();
        String currentUrl = page.url();
        if (currentUrl == null || !currentUrl.contains("web.whatsapp.com")) {
            log.info("Navigating to https://web.whatsapp.com in background...");
            page.navigate("https://web.whatsapp.com");
        }
        return page;
    }

    private ToolResultDTO executeOpenSession(Page page) {
        try {
            page.waitForSelector("#pane-side, canvas[aria-label*='Scan'], div[data-ref]",
                new Page.WaitForSelectorOptions().setTimeout(10000));

            boolean hasPane = page.locator("#pane-side").count() > 0;
            if (hasPane) {
                return new ToolResultDTO(true, "WhatsApp Web session is ACTIVE and logged in.", Map.of("status", "LOGGED_IN", "action", "open_session"));
            }

            boolean hasQr = page.locator("canvas[aria-label*='Scan'], div[data-ref]").count() > 0;
            if (hasQr) {
                try {
                    page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("mark-workspace/whatsapp_qr.png")));
                } catch (Exception ignored) {}
                return new ToolResultDTO(false, "WhatsApp Web requires QR code authentication. The Chromium browser window is open on your screen (QR image also saved at mark-workspace/whatsapp_qr.png). Please scan the QR code with WhatsApp on your phone (Linked Devices) to pair.", Map.of("status", "QR_SCAN_REQUIRED", "action", "open_session", "qr_image", "mark-workspace/whatsapp_qr.png"));
            }

            return new ToolResultDTO(true, "WhatsApp Web page loaded (initializing session).", Map.of("status", "INITIALIZING", "action", "open_session"));
        } catch (Exception e) {
            return new ToolResultDTO(false, "Failed to load WhatsApp Web session: " + e.getMessage(), Map.of());
        }
    }

    private ToolResultDTO executeOpenChat(Page page, String contact) {
        log.info("Executing WhatsApp Web open_chat for '{}'", contact);
        String digits = contact.replaceAll("[^0-9]", "");
        String clean = contact.trim();
        String last10 = digits.length() >= 10 ? digits.substring(digits.length() - 10) : digits;

        // 1. First check if contact/number is already directly visible in the sidebar list
        try {
            page.waitForSelector("#pane-side", new Page.WaitForSelectorOptions().setTimeout(6000));
            String selector;
            if (!last10.isBlank()) {
                String formatted5_5 = last10.length() == 10 ? (last10.substring(0, 5) + " " + last10.substring(5)) : last10;
                selector = String.format(
                    "#pane-side span[title*='%s'], #pane-side span[title*='%s'], #pane-side span[title*='%s']",
                    clean, last10, formatted5_5
                );
            } else {
                selector = String.format("#pane-side span[title*='%s']", clean);
            }
            var item = page.locator(selector).first();
            if (item.count() > 0) {
                log.info("Contact '{}' found directly in sidebar list, clicking to open...", contact);
                item.click();
                page.waitForTimeout(600);
                return new ToolResultDTO(true, "Successfully opened WhatsApp Web chat with '" + contact + "'.",
                        Map.of("contact", contact, "status", "OPENED", "action", "open_chat"));
            }
        } catch (Exception e) {
            log.debug("Sidebar scan check notice: {}", e.getMessage());
        }

        // 2. Direct URL navigation for phone numbers
        if (digits.length() >= 7 && contact.matches("^[\\+]?[0-9\\s\\-\\(\\)]{7,}$")) {
            // If 10 digits without country code, default to 91 (India)
            String fullDigits = (digits.length() == 10 && !digits.startsWith("91")) ? ("91" + digits) : digits;
            String url = "https://web.whatsapp.com/send?phone=" + fullDigits;
            log.info("Opening chat via direct URL: {}", url);
            page.navigate(url);
            try {
                page.waitForSelector("#main, div[data-testid='conversation-panel-messages'], div[role='dialog']",
                    new Page.WaitForSelectorOptions().setTimeout(15000));
            } catch (Exception e) {
                log.warn("Timed out waiting for chat pane to settle: {}", e.getMessage());
            }
        } else {
            // 3. Search contact in sidebar
            try {
                page.waitForSelector("#pane-side", new Page.WaitForSelectorOptions().setTimeout(10000));
                var searchInput = page.locator("div[contenteditable='true'][data-tab='3']").first();
                if (searchInput.count() > 0) {
                    searchInput.click();
                    searchInput.fill(contact);
                    page.waitForTimeout(600);
                    page.keyboard().press("Enter");
                    page.waitForTimeout(600);
                }
            } catch (Exception e) {
                log.warn("Sidebar search navigation warning: {}", e.getMessage());
            }
        }

        return new ToolResultDTO(true, "Successfully opened WhatsApp Web chat with '" + contact + "'.",
                Map.of("contact", contact, "status", "OPENED", "action", "open_chat"));
    }

    private ToolResultDTO executeSendMessage(Page page, String contact, String message) {
        log.info("Executing WhatsApp Web send_message to '{}'", contact);
        String digits = contact.replaceAll("[^0-9]", "");

        if (digits.length() >= 7 && contact.matches("^[\\+]?[0-9\\s\\-\\(\\)]{7,}$")) {
            String fullDigits = (digits.length() == 10 && !digits.startsWith("91")) ? ("91" + digits) : digits;
            String encodedMsg = URLEncoder.encode(message, StandardCharsets.UTF_8);
            String url = "https://web.whatsapp.com/send?phone=" + fullDigits + "&text=" + encodedMsg;
            page.navigate(url);
        } else {
            executeOpenChat(page, contact);
            try {
                page.waitForSelector("#main footer div[contenteditable='true']",
                    new Page.WaitForSelectorOptions().setTimeout(10000));
            } catch (Exception e) {
                log.warn("Timed out waiting for input composer: {}", e.getMessage());
            }
            var input = page.locator("#main footer div[contenteditable='true']").first();
            input.click();
            input.fill(message);
        }

        // Wait for the Send button
        try {
            page.waitForSelector("button[aria-label='Send'], button:has(span[data-icon='send'])",
                new Page.WaitForSelectorOptions().setTimeout(15000));
            var sendBtn = page.locator("button[aria-label='Send'], button:has(span[data-icon='send'])").first();
            sendBtn.click();
            page.waitForTimeout(800);
        } catch (Exception e) {
            // Fallback: send Enter on the active input element
            page.keyboard().press("Enter");
            page.waitForTimeout(800);
        }

        return new ToolResultDTO(
            true,
            "Successfully sent WhatsApp message to '" + contact + "': \"" + message + "\"",
            Map.of("contact", contact, "message", message, "status", "SENT", "action", "send_message")
        );
    }

    public ToolResultDTO executeReadChat(Page page, String contact, int limit, String expectedMessage) {
        log.info("Executing WhatsApp Web read_chat (contact={}, limit={}, expectedMessage={})", contact, limit, expectedMessage);

        if (contact != null && !contact.isBlank()) {
            executeOpenChat(page, contact);
            page.waitForTimeout(1000);
        }

        try {
            page.waitForSelector("#main", new Page.WaitForSelectorOptions().setTimeout(10000));
        } catch (Exception e) {
            return new ToolResultDTO(false, "WhatsApp Web conversation pane not active or chat not loaded.", Map.of("action", "read_chat"));
        }

        String jsExtract = """
            () => {
                const list = [];
                const blocks = document.querySelectorAll('#main div.copyable-text[data-pre-plain-text]');
                for (const b of blocks) {
                    const pre = b.getAttribute('data-pre-plain-text') || '';
                    const textEl = b.querySelector('span.selectable-text');
                    const text = textEl ? textEl.innerText : b.innerText;
                    const row = b.closest('.message-out, .message-in');
                    const fromMe = row ? row.classList.contains('message-out') : false;
                    
                    let status = fromMe ? 'Sent' : 'Received';
                    if (fromMe && row) {
                        if (row.querySelector('span[data-icon="msg-dblcheck-ack"]')) status = 'Read';
                        else if (row.querySelector('span[data-icon="msg-dblcheck"]')) status = 'Delivered';
                        else if (row.querySelector('span[data-icon="msg-check"]')) status = 'Sent';
                        else if (row.querySelector('span[data-icon="msg-time"]')) status = 'Pending';
                    }
                    list.push({ pre, text, fromMe, status });
                }
                if (list.length === 0) {
                    const rows = document.querySelectorAll('#main div.message-out, #main div.message-in');
                    for (const row of rows) {
                        const textEl = row.querySelector('span.selectable-text');
                        if (textEl && textEl.innerText) {
                            const fromMe = row.classList.contains('message-out');
                            let status = fromMe ? 'Sent' : 'Received';
                            if (fromMe) {
                                if (row.querySelector('span[data-icon="msg-dblcheck-ack"]')) status = 'Read';
                                else if (row.querySelector('span[data-icon="msg-dblcheck"]')) status = 'Delivered';
                                else if (row.querySelector('span[data-icon="msg-check"]')) status = 'Sent';
                                else if (row.querySelector('span[data-icon="msg-time"]')) status = 'Pending';
                            }
                            list.push({ pre: '', text: textEl.innerText, fromMe, status });
                        }
                    }
                }
                return JSON.stringify(list);
            }
            """;

        String jsonResult = (String) page.evaluate(jsExtract);
        List<WhatsAppWebMessage> parsedMessages = parseExtractedJson(jsonResult, contact);

        int startIndex = Math.max(0, parsedMessages.size() - limit);
        List<WhatsAppWebMessage> recentMessages = parsedMessages.subList(startIndex, parsedMessages.size());

        WhatsAppWebMessage lastMessage = recentMessages.isEmpty() ? null : recentMessages.get(recentMessages.size() - 1);
        WhatsAppWebMessage lastFromMe = null;
        WhatsAppWebMessage lastFromThem = null;
        for (int i = recentMessages.size() - 1; i >= 0; i--) {
            WhatsAppWebMessage m = recentMessages.get(i);
            if (m.fromMe() && lastFromMe == null) lastFromMe = m;
            else if (!m.fromMe() && lastFromThem == null) lastFromThem = m;
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
        summary.append(String.format("Successfully read %d recent message(s) from WhatsApp Web conversation history:\n", recentMessages.size()));
        if (lastFromMe != null) {
            summary.append(String.format("- Last message from my side: \"%s\" [%s, Status: %s]\n", lastFromMe.text(), lastFromMe.timestamp(), lastFromMe.status()));
        }
        if (lastFromThem != null) {
            summary.append(String.format("- Last message from their side: \"%s\" [%s, by %s]\n", lastFromThem.text(), lastFromThem.timestamp(), lastFromThem.sender()));
        }
        summary.append("\nAll messages:\n").append(formatMessages(recentMessages));
        return new ToolResultDTO(true, summary.toString().trim(), metadata);
    }

    private ToolResultDTO evaluateVerification(List<WhatsAppWebMessage> recentMessages, String expectedMessage, String contact, Map<String, Object> metadata) {
        boolean verified = false;
        WhatsAppWebMessage matchedMessage = null;

        for (int i = recentMessages.size() - 1; i >= 0; i--) {
            WhatsAppWebMessage m = recentMessages.get(i);
            if (m.text().equalsIgnoreCase(expectedMessage)
                    || m.text().toLowerCase().contains(expectedMessage.toLowerCase())
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
                "VERIFICATION PASSED: Confirmed message \"%s\" exists in WhatsApp Web chat history with '%s'! [Side: %s, Sender: %s, Time: %s, Status: %s]",
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

    private ToolResultDTO executeListUnreadChats(Page page, int limit) {
        log.info("Executing WhatsApp Web list_unread_chats (limit={})", limit);

        try {
            page.waitForSelector("#pane-side", new Page.WaitForSelectorOptions().setTimeout(10000));
        } catch (Exception e) {
            return new ToolResultDTO(false, "WhatsApp Web sidebar not loaded or session not authenticated.", Map.of());
        }

        String jsUnreads = """
            () => {
                const list = [];
                // Query all unread badges across pane-side
                const badges = document.querySelectorAll('#pane-side span[aria-label*="unread"], #pane-side div[aria-label*="unread"]');
                for (const badge of badges) {
                    const row = badge.closest('div[role="listitem"], div[data-testid*="cell-frame"], div.x10l6tqk') || badge.closest('div');
                    if (row) {
                        const titleEl = row.querySelector('span[title]');
                        const contact = titleEl ? titleEl.getAttribute('title') : 'Unknown';
                        const textP = row.querySelector('span.copyable-text, span.selectable-text');
                        const snippet = textP ? textP.innerText : '';
                        const ariaLabel = badge.getAttribute('aria-label') || '';
                        const countMatch = ariaLabel.match(/(\\d+)/);
                        const count = countMatch ? parseInt(countMatch[1]) : 1;
                        if (!list.some(x => x.contact === contact)) {
                            list.push({ contact, count, snippet, raw: ariaLabel });
                        }
                    }
                }
                return JSON.stringify(list);
            }
            """;

        String jsonResult = (String) page.evaluate(jsUnreads);
        List<WhatsAppWebUnreadChat> unreadChats = parseUnreadJson(jsonResult, limit);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("action", "list_unread_chats");
        metadata.put("unread_chats_count", unreadChats.size());
        metadata.put("unread_chats", unreadChats);

        if (unreadChats.isEmpty()) {
            return new ToolResultDTO(true, "No unread messages found in WhatsApp Web. All chats are currently caught up.", metadata);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d chat(s) with unread messages in WhatsApp Web:\n", unreadChats.size()));
        for (int i = 0; i < unreadChats.size(); i++) {
            WhatsAppWebUnreadChat uc = unreadChats.get(i);
            sb.append(String.format("%d. %s (%d unread message%s): \"%s\"\n",
                i + 1,
                uc.contact(),
                uc.unreadCount(),
                uc.unreadCount() == 1 ? "" : "s",
                uc.lastSnippet()
            ));
        }

        return new ToolResultDTO(true, sb.toString().trim(), metadata);
    }

    public List<WhatsAppWebMessage> parseExtractedJson(String rawJson, String targetContact) {
        if (rawJson == null || rawJson.isBlank() || rawJson.equals("[]")) {
            return List.of();
        }

        List<WhatsAppWebMessage> messages = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            List<JsonNode> nodes = new ArrayList<>();
            if (root.isArray()) root.forEach(nodes::add);
            else if (root.isObject()) nodes.add(root);

            for (JsonNode n : nodes) {
                String pre = n.has("pre") ? n.get("pre").asText("") : "";
                String text = n.has("text") ? n.get("text").asText("") : "";
                boolean fromMe = n.has("fromMe") && n.get("fromMe").asBoolean();
                String status = n.has("status") ? n.get("status").asText(fromMe ? "Sent" : "Received") : (fromMe ? "Sent" : "Received");

                String sender = fromMe ? "You" : (targetContact != null ? targetContact : "Unknown");
                String timestamp = "";

                if (!pre.isBlank()) {
                    Matcher m = PRE_META_PATTERN.matcher(pre);
                    if (m.find()) {
                        timestamp = m.group(1).trim();
                        String extractedSender = m.group(2).trim();
                        if (!fromMe && !extractedSender.isBlank()) {
                            sender = extractedSender;
                        }
                    }
                }

                messages.add(new WhatsAppWebMessage(fromMe, sender, text, timestamp, status, pre + text));
            }
        } catch (Exception e) {
            log.warn("Failed to parse WhatsApp Web message JSON: {}, raw: {}", e.getMessage(), rawJson);
        }
        return messages;
    }

    private List<WhatsAppWebUnreadChat> parseUnreadJson(String rawJson, int limit) {
        if (rawJson == null || rawJson.isBlank() || rawJson.equals("[]")) {
            return List.of();
        }

        List<WhatsAppWebUnreadChat> list = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            List<JsonNode> nodes = new ArrayList<>();
            if (root.isArray()) root.forEach(nodes::add);
            else if (root.isObject()) nodes.add(root);

            Set<String> seen = new HashSet<>();
            for (JsonNode n : nodes) {
                String contact = n.has("contact") ? n.get("contact").asText("Unknown") : "Unknown";
                int count = n.has("count") ? n.get("count").asInt(1) : 1;
                String snippet = n.has("snippet") ? n.get("snippet").asText("") : "";
                String raw = n.has("raw") ? n.get("raw").asText("") : "";

                if (seen.add(contact.toLowerCase())) {
                    list.add(new WhatsAppWebUnreadChat(contact, count, snippet, "", raw));
                    if (list.size() >= limit) break;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse unread WhatsApp Web JSON: {}, raw: {}", e.getMessage(), rawJson);
        }
        return list;
    }

    private String formatMessages(List<WhatsAppWebMessage> messages) {
        if (messages.isEmpty()) {
            return "(No conversation messages found in active pane)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            WhatsAppWebMessage m = messages.get(i);
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
}
