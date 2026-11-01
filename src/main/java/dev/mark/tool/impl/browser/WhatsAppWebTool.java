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
import java.nio.file.Files;
import java.nio.file.Path;
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
    public record WhatsAppWebStatusItem(String contact, String timestamp, boolean unseen, String raw) {}
    public record WhatsAppWebMyStatus(boolean hasActiveStatus, String timestamp, int viewCount, List<String> viewers) {}

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
                + "Actions: 'open_session', 'send_message', 'open_chat', 'read_chat', 'verify_message', 'list_unread_chats', "
                + "'list_unseen_statuses', 'get_my_status', 'post_status', 'view_contact_status', 'start_audio_call', 'start_video_call'. "
                + "Arguments: action (required), contact (phone number with country code or contact name), message, expected_message, limit, file_path.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("action"),
            "properties", Map.of(
                "action", Map.of(
                    "type", "string",
                    "enum", List.of(
                        "open_session", "send_message", "open_chat", "read_chat", "verify_message", "list_unread_chats",
                        "list_unseen_statuses", "get_my_status", "post_status", "view_contact_status", "start_audio_call", "start_video_call"
                    ),
                    "description", "WhatsApp Web action to perform"
                ),
                "contact", Map.of(
                    "type", "string",
                    "description", "Phone number with country code (e.g. 8280901077) or contact name"
                ),
                "message", Map.of(
                    "type", "string",
                    "description", "Message text content to send in chat, text status to publish, or caption for photo/media status"
                ),
                "expected_message", Map.of(
                    "type", "string",
                    "description", "Expected message content to verify in chat history (read-only verification)"
                ),
                "file_path", Map.of(
                    "type", "string",
                    "description", "Path or filename of image, photo, or file to post as status or attach (supports workspace-relative paths)"
                ),
                "limit", Map.of(
                    "type", "integer",
                    "description", "Maximum number of recent messages/statuses to read (default: 10)"
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

                case "list_unseen_statuses":
                    return executeListUnseenStatuses(page, limit);

                case "get_my_status":
                    return executeGetMyStatus(page);

                case "post_status":
                    String statusText = (String) arguments.get("message");
                    if (statusText == null || statusText.isBlank()) {
                        statusText = (String) arguments.get("caption");
                    }
                    String filePath = (String) arguments.get("file_path");
                    if (filePath == null || filePath.isBlank()) {
                        filePath = (String) arguments.get("media_path");
                    }
                    if (filePath == null || filePath.isBlank()) {
                        filePath = (String) arguments.get("filePath");
                    }
                    return executePostStatus(page, statusText, filePath);

                case "view_contact_status":
                    return executeViewContactStatus(page, contact);

                case "start_audio_call":
                    return executeStartCall(page, contact, false);

                case "start_video_call":
                    return executeStartCall(page, contact, true);

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

    private boolean ensureStatusPanel(Page page) {
        try {
            var statusHeader = page.locator("#side header:has-text('Status'), div[data-testid='status-panel']").first();
            if (statusHeader.count() > 0 && statusHeader.isVisible()) {
                return true;
            }
            var statusBtn = page.locator("button[aria-label*='Status' i], div[role='button'][aria-label*='Status' i], button:has(span[data-icon*='status']), span[data-icon*='status']").first();
            if (statusBtn.count() > 0) {
                statusBtn.click();
                page.waitForTimeout(1000);
                return true;
            }
        } catch (Exception e) {
            log.warn("Notice switching to Status panel: {}", e.getMessage());
        }
        return false;
    }

    private void ensureChatsPanel(Page page) {
        try {
            var chatsBtn = page.locator("button[aria-label*='Chats' i], div[role='button'][aria-label*='Chats' i], button:has(span[data-icon*='chat']), span[data-icon*='chat']").first();
            if (chatsBtn.count() > 0) {
                chatsBtn.click();
                page.waitForTimeout(600);
            }
        } catch (Exception e) {
            log.debug("Notice switching back to chats: {}", e.getMessage());
        }
    }

    private ToolResultDTO executeListUnseenStatuses(Page page, int limit) {
        log.info("Executing WhatsApp Web list_unseen_statuses (limit={})", limit);
        ensureStatusPanel(page);

        String jsExtractStatuses = """
            () => {
                const results = [];
                let badgeCount = 0;
                const statusBtn = document.querySelector('button[aria-label*="Status"], button:has(span[data-icon*="status"])');
                if (statusBtn) {
                    const badge = statusBtn.querySelector('span[aria-label*="unread"], span.x178xt8z');
                    if (badge) {
                        const aria = badge.getAttribute('aria-label') || '';
                        const m = aria.match(/(\\d+)/);
                        badgeCount = m ? parseInt(m[1]) : 1;
                    }
                }

                const rows = document.querySelectorAll('div[data-testid="status-item"], div[role="button"]:has(span[title]), div#pane-side div[role="button"]');
                let isRecentSection = true;

                for (const row of rows) {
                    const titleEl = row.querySelector('span[title]');
                    const name = titleEl ? titleEl.getAttribute('title') : '';
                    if (!name || name.toLowerCase() === 'my status' || name.toLowerCase().includes('status')) {
                        continue;
                    }

                    const svgRing = row.querySelector('svg');
                    let unseen = false;
                    if (svgRing) {
                        const stroke = svgRing.getAttribute('stroke') || '';
                        const style = svgRing.getAttribute('style') || '';
                        const html = svgRing.outerHTML || '';
                        if (html.includes('00a884') || html.includes('status-v3-unread') || html.includes('var(--status-ring-unread)') || stroke.includes('green') || style.includes('green')) {
                            unseen = true;
                        }
                    }

                    if (!unseen && isRecentSection) {
                        const text = row.innerText || '';
                        if (text.includes('Viewed')) {
                            isRecentSection = false;
                        } else {
                            unseen = true;
                        }
                    }

                    let time = '';
                    const textElements = row.querySelectorAll('span, div');
                    for (const el of textElements) {
                        const t = (el.innerText || '').trim();
                        if (t.includes('Today') || t.includes('Yesterday') || t.includes('ago') || t.includes(':')) {
                            time = t;
                            break;
                        }
                    }

                    if (!results.some(r => r.contact.toLowerCase() === name.toLowerCase())) {
                        results.push({ contact: name, timestamp: time, unseen: unseen });
                    }
                }

                return JSON.stringify({ badgeCount: badgeCount, items: results });
            }
            """;

        String jsonResult = (String) page.evaluate(jsExtractStatuses);
        List<WhatsAppWebStatusItem> allStatuses = parseStatusesJson(jsonResult, limit);
        List<WhatsAppWebStatusItem> unseenStatuses = allStatuses.stream().filter(WhatsAppWebStatusItem::unseen).toList();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("action", "list_unseen_statuses");
        metadata.put("unseen_count", unseenStatuses.size());
        metadata.put("total_status_updates", allStatuses.size());
        metadata.put("unseen_statuses", unseenStatuses);

        ensureChatsPanel(page);

        if (unseenStatuses.isEmpty()) {
            return new ToolResultDTO(true, "No unseen WhatsApp statuses found. All status updates have been viewed or none are available.", metadata);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d unseen contact status update(s) in WhatsApp Web:\n", unseenStatuses.size()));
        for (int i = 0; i < unseenStatuses.size(); i++) {
            WhatsAppWebStatusItem s = unseenStatuses.get(i);
            sb.append(String.format("%d. %s (%s)\n", i + 1, s.contact(), s.timestamp().isBlank() ? "Recent" : s.timestamp()));
        }

        return new ToolResultDTO(true, sb.toString().trim(), metadata);
    }

    private ToolResultDTO executeGetMyStatus(Page page) {
        log.info("Executing WhatsApp Web get_my_status");
        ensureStatusPanel(page);

        String jsMyStatus = """
            () => {
                const allElements = Array.from(document.querySelectorAll('button, div[role="button"], div[data-testid="status-item"]'));
                const myStatusRow = allElements.find(el => {
                    const txt = (el.innerText || '').trim();
                    return txt.startsWith('My status') || txt.includes('My status');
                });

                if (!myStatusRow) {
                    return JSON.stringify({ hasActiveStatus: false, text: "My status section not found", viewCount: 0 });
                }

                const text = myStatusRow.innerText || '';
                if (text.includes("Click to add status update") || text.includes("Tap to add status update") || text.includes("No updates") || text.includes("Add to my status")) {
                    return JSON.stringify({ hasActiveStatus: false, text: "No active status update posted.", viewCount: 0 });
                }

                let viewCount = 0;
                const viewMatch = text.match(/(\\d+)\\s*(views|viewed by|view)/i) || text.match(/viewed by\\s*(\\d+)/i);
                if (viewMatch) {
                    viewCount = parseInt(viewMatch[1]);
                } else {
                    const eyeIcon = myStatusRow.querySelector('span[data-icon="view-status"], svg');
                    if (eyeIcon && eyeIcon.parentElement) {
                        const countText = eyeIcon.parentElement.innerText || '';
                        const digitMatch = countText.match(/(\\d+)/);
                        if (digitMatch) viewCount = parseInt(digitMatch[1]);
                    }
                }

                let time = "Active";
                const lines = text.split('\\n').map(l => l.trim()).filter(l => l && !l.toLowerCase().includes('my status'));
                for (const line of lines) {
                    if (line.includes('Today') || line.includes('Yesterday') || line.includes(':') || line.includes('ago') || line.includes('Just now')) {
                        time = line;
                        break;
                    }
                }

                return JSON.stringify({ hasActiveStatus: true, timestamp: time, viewCount: viewCount, raw: text });
            }
            """;

        String jsonResult = (String) page.evaluate(jsMyStatus);
        ensureChatsPanel(page);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("action", "get_my_status");

        try {
            JsonNode root = objectMapper.readTree(jsonResult);
            boolean hasActiveStatus = root.has("hasActiveStatus") && root.get("hasActiveStatus").asBoolean();
            int viewCount = root.has("viewCount") ? root.get("viewCount").asInt(0) : 0;
            String timestamp = root.has("timestamp") ? root.get("timestamp").asText("") : "";

            metadata.put("has_active_status", hasActiveStatus);
            metadata.put("view_count", viewCount);
            metadata.put("timestamp", timestamp);

            if (!hasActiveStatus) {
                return new ToolResultDTO(true, "You currently have NO active status posted on WhatsApp Web.", metadata);
            } else {
                String summary = String.format("You have an ACTIVE WhatsApp status posted (%s). Viewed by %d people.",
                    timestamp.isBlank() ? "Recently" : timestamp, viewCount);
                return new ToolResultDTO(true, summary, metadata);
            }
        } catch (Exception e) {
            return new ToolResultDTO(false, "Failed to inspect My Status: " + e.getMessage(), metadata);
        }
    }

    private Path resolveFilePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return null;
        try {
            // 1. Direct path check
            Path p = Paths.get(rawPath);
            if (Files.exists(p)) return p.toAbsolutePath().normalize();

            // 2. Relative to current working directory (workspace root)
            Path workspaceDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
            Path workspaceRelative = workspaceDir.resolve(rawPath).normalize();
            if (Files.exists(workspaceRelative)) return workspaceRelative.toAbsolutePath();

            // 3. Parent directory (e.g. if mark is inside Desktop, parent is Desktop)
            if (workspaceDir.getParent() != null) {
                Path parentRelative = workspaceDir.getParent().resolve(rawPath).normalize();
                if (Files.exists(parentRelative)) return parentRelative.toAbsolutePath();
            }

            // 4. Desktop resolution (handles both standard Desktop and OneDrive Desktop)
            String strippedDesktop = rawPath.replace('\\', '/');
            if (strippedDesktop.startsWith("Desktop/")) {
                strippedDesktop = strippedDesktop.substring(8);
            } else if (strippedDesktop.equals("Desktop")) {
                strippedDesktop = "";
            }

            String userHome = System.getProperty("user.home", "");
            if (!userHome.isBlank()) {
                // OneDrive Desktop
                Path oneDriveDesktop = Paths.get(userHome, "OneDrive", "Desktop").resolve(strippedDesktop).normalize();
                if (Files.exists(oneDriveDesktop)) return oneDriveDesktop.toAbsolutePath();

                // Standard Desktop
                Path stdDesktop = Paths.get(userHome, "Desktop").resolve(strippedDesktop).normalize();
                if (Files.exists(stdDesktop)) return stdDesktop.toAbsolutePath();

                // User home direct
                Path homeRelative = Paths.get(userHome).resolve(rawPath).normalize();
                if (Files.exists(homeRelative)) return homeRelative.toAbsolutePath();
            }
        } catch (Exception e) {
            log.warn("Error resolving file path '{}': {}", rawPath, e.getMessage());
        }
        return null;
    }

    private ToolResultDTO executePostStatus(Page page, String text) {
        return executePostStatus(page, text, null);
    }

    private void postSingleMediaStatus(Page page, Path file, String caption) {
        var fileInput = page.locator("input[accept*='image'], input[accept*='video'], input[type='file']").first();
        if (fileInput.count() == 0) {
            var addBtn = page.locator("button[aria-label*='Add status' i], button:has(span[data-icon='status-add']), div[role='button']:has-text('Add status'), div[role='button']:has-text('My status')").first();
            if (addBtn.count() > 0) {
                addBtn.click();
                page.waitForTimeout(500);
            }
        }

        fileInput = page.locator("input[accept*='image'], input[accept*='video'], input[type='file']").first();
        if (fileInput.count() > 0) {
            fileInput.setInputFiles(file);
        } else {
            final Path targetFile = file;
            var fileChooser = page.waitForFileChooser(new Page.WaitForFileChooserOptions().setTimeout(4000), () -> {
                var photoOption = page.locator("button:has-text('Photos'), li:has-text('Photos'), div[role='button']:has-text('Photos'), span[data-icon='image'], span[data-icon='camera']").first();
                if (photoOption.count() > 0) {
                    photoOption.click();
                }
            });
            fileChooser.setFiles(targetFile);
        }

        page.waitForSelector("button[aria-label='Send'], button:has(span[data-icon='send']), span[data-icon='send'], div[contenteditable='true']",
            new Page.WaitForSelectorOptions().setTimeout(12000));

        if (caption != null && !caption.isBlank()) {
            var captionBox = page.locator("div[contenteditable='true'], div[role='textbox']").first();
            if (captionBox.count() > 0) {
                captionBox.click();
                captionBox.fill(caption);
                page.waitForTimeout(500);
            }
        }

        var sendBtn = page.locator("button[aria-label='Send'], button:has(span[data-icon='send']), span[data-icon='send']").first();
        if (sendBtn.count() > 0) {
            sendBtn.click();
        } else {
            page.keyboard().press("Enter");
        }
        page.waitForTimeout(2500);
    }

    private ToolResultDTO executePostStatus(Page page, String text, String rawFilePath) {
        log.info("Executing WhatsApp Web post_status: text=\"{}\", file=\"{}\"", text, rawFilePath);

        boolean hasFile = rawFilePath != null && !rawFilePath.isBlank();
        Path resolvedFile = null;

        if (hasFile) {
            resolvedFile = resolveFilePath(rawFilePath);
            if (resolvedFile == null) {
                return new ToolResultDTO(false,
                    "Media file not found for status: '" + rawFilePath + "'. Please verify the file exists in the workspace or specify a valid path.",
                    Map.of("action", "post_status", "file_path", rawFilePath));
            }
        } else {
            if (text == null || text.isBlank()) {
                return new ToolResultDTO(false, "Argument 'message' (for text status) or 'file_path' (for photo/media status) is required for 'post_status'.", Map.of());
            }
        }

        ensureStatusPanel(page);

        try {
            if (hasFile) {
                if (Files.isDirectory(resolvedFile)) {
                    List<Path> imageFiles = new ArrayList<>();
                    try (var stream = Files.list(resolvedFile)) {
                        imageFiles = stream
                            .filter(Files::isRegularFile)
                            .filter(path -> {
                                String name = path.getFileName().toString().toLowerCase();
                                return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp");
                            })
                            .sorted(Comparator.comparing(Path::getFileName))
                            .toList();
                    }

                    if (imageFiles.isEmpty()) {
                        ensureChatsPanel(page);
                        return new ToolResultDTO(false, "No valid image files (.jpg, .jpeg, .png, .webp) found in directory: " + resolvedFile,
                            Map.of("action", "post_status", "file_path", resolvedFile.toString()));
                    }

                    log.info("Found {} images in directory '{}', posting each to WhatsApp Web status...", imageFiles.size(), resolvedFile);

                    int postedCount = 0;
                    for (int i = 0; i < imageFiles.size(); i++) {
                        Path img = imageFiles.get(i);
                        String itemCaption = text;
                        if (text != null && !text.isBlank()) {
                            if (imageFiles.size() > 1) {
                                itemCaption = text.trim() + " (" + (i + 1) + "/" + imageFiles.size() + ")";
                            }
                        }

                        postSingleMediaStatus(page, img, itemCaption);
                        postedCount++;
                        page.waitForTimeout(1500);
                    }

                    ensureChatsPanel(page);

                    String summary = String.format("Successfully posted %d photo status updates from directory '%s' to WhatsApp Web",
                        postedCount, resolvedFile.getFileName());
                    if (text != null && !text.isBlank()) {
                        summary += " with captions starting with: \"" + text + "\"";
                    }

                    Map<String, Object> meta = new HashMap<>();
                    meta.put("action", "post_status");
                    meta.put("type", "DIRECTORY_MEDIA");
                    meta.put("status", "POSTED");
                    meta.put("posted_count", postedCount);
                    meta.put("directory", resolvedFile.toString());
                    if (text != null && !text.isBlank()) meta.put("caption_prefix", text);

                    return new ToolResultDTO(true, summary, meta);
                } else {
                    // Single photo / video status upload
                    postSingleMediaStatus(page, resolvedFile, text);
                    ensureChatsPanel(page);

                    String msg = "Successfully posted new WhatsApp photo status with file '" + resolvedFile.getFileName() + "'";
                    if (text != null && !text.isBlank()) {
                        msg += " and caption: \"" + text + "\"";
                    }

                    Map<String, Object> meta = new HashMap<>();
                    meta.put("action", "post_status");
                    meta.put("type", "MEDIA");
                    meta.put("status", "POSTED");
                    meta.put("file_path", resolvedFile.toString());
                    if (text != null && !text.isBlank()) meta.put("caption", text);

                    return new ToolResultDTO(true, msg, meta);
                }
            } else {
                // Pure text status
                var pencilBtn = page.locator("button[aria-label*='Text' i], button[aria-label*='Add status' i], button:has(span[data-icon='status-pencil']), button:has(span[data-icon='pencil'])").first();
                if (pencilBtn.count() == 0) {
                    pencilBtn = page.locator("div[role='button']:has-text('My status') button, span[data-icon='status-pencil']").first();
                }

                if (pencilBtn.count() > 0) {
                    pencilBtn.click();
                    page.waitForTimeout(1000);
                } else {
                    ensureChatsPanel(page);
                    return new ToolResultDTO(false, "Could not find WhatsApp Web Status creation button.", Map.of("action", "post_status"));
                }

                page.waitForSelector("div[contenteditable='true'], div[role='textbox']",
                    new Page.WaitForSelectorOptions().setTimeout(10000));
                var composer = page.locator("div[contenteditable='true'], div[role='textbox']").first();
                composer.click();
                composer.fill(text);
                page.waitForTimeout(600);

                var sendBtn = page.locator("button[aria-label='Send'], button:has(span[data-icon='send']), span[data-icon='send']").first();
                if (sendBtn.count() > 0) {
                    sendBtn.click();
                } else {
                    page.keyboard().press("Enter");
                }
                page.waitForTimeout(1500);

                ensureChatsPanel(page);
                return new ToolResultDTO(true, "Successfully posted new WhatsApp status update: \"" + text + "\"",
                    Map.of("action", "post_status", "type", "TEXT", "status", "POSTED", "message", text));
            }
        } catch (Exception e) {
            ensureChatsPanel(page);
            return new ToolResultDTO(false, "Failed to post status: " + e.getMessage(), Map.of("action", "post_status"));
        }
    }

    private ToolResultDTO executeViewContactStatus(Page page, String contact) {
        log.info("Executing WhatsApp Web view_contact_status for '{}'", contact);
        if (contact == null || contact.isBlank()) {
            return new ToolResultDTO(false, "Argument 'contact' is required for 'view_contact_status'.", Map.of());
        }

        ensureStatusPanel(page);
        page.waitForTimeout(1000);

        try {
            String jsExpandAndClick = """
                (contactName) => {
                    // 1. Try to expand 'Viewed' or 'Viewed updates' accordion if collapsed
                    const buttons = Array.from(document.querySelectorAll('div[role="button"], button, div.x1280gxy'));
                    const viewedHeader = buttons.find(b => {
                        const txt = (b.innerText || '').trim();
                        return (txt.startsWith('Viewed updates') || txt.startsWith('Viewed')) && !txt.includes('\\n');
                    });
                    if (viewedHeader) {
                        const isExpanded = viewedHeader.getAttribute('aria-expanded');
                        const hasChevronRight = viewedHeader.querySelector('span[data-icon="chevron-right"], span[data-icon="chevron-down"]');
                        if (isExpanded === 'false' || (hasChevronRight && viewedHeader.innerHTML.includes('chevron-right'))) {
                            viewedHeader.click();
                        }
                    }

                    // 2. Locate contact status row in the Status panel
                    const rows = Array.from(document.querySelectorAll('div[role="button"], div[data-testid="status-item"], button'));
                    const match = rows.find(r => {
                        const text = (r.innerText || '').toLowerCase();
                        const titleEl = r.querySelector('span[title]');
                        const title = (titleEl ? titleEl.getAttribute('title') || titleEl.innerText : '').toLowerCase();
                        const target = contactName.toLowerCase();
                        const isMyStatus = text.includes('my status');
                        const isSearch = text.includes('search');
                        const isHeader = text.startsWith('recent') || text.startsWith('viewed');
                        return !isMyStatus && !isSearch && !isHeader && (title === target || title.includes(target) || text.includes(target));
                    });

                    if (match) {
                        match.scrollIntoView({ block: 'center', behavior: 'instant' });
                        match.click();
                        return JSON.stringify({ found: true });
                    }
                    return JSON.stringify({ found: false });
                }
                """;

            String jsonResult = (String) page.evaluate(jsExpandAndClick, contact);
            boolean found = false;
            try {
                JsonNode root = objectMapper.readTree(jsonResult);
                found = root.has("found") && root.get("found").asBoolean();
            } catch (Exception ignored) {}

            if (!found) {
                // Fallback Playwright interactive button selector
                var item = page.locator(String.format("div[role='button']:has-text('%s'), button:has-text('%s')", contact, contact)).first();
                if (item.count() > 0) {
                    item.click();
                    found = true;
                }
            }

            if (!found) {
                ensureChatsPanel(page);
                return new ToolResultDTO(false, "No status update found for contact '" + contact + "' in WhatsApp Web (checked Recent and Viewed updates).",
                    Map.of("contact", contact, "action", "view_contact_status"));
            }

            page.waitForTimeout(4000);

            var closeBtn = page.locator("button[aria-label='Close'], button:has(span[data-icon='x-viewer']), span[data-icon='x-viewer']").first();
            if (closeBtn.count() > 0) {
                closeBtn.click();
            } else {
                page.keyboard().press("Escape");
            }
            page.waitForTimeout(800);

            ensureChatsPanel(page);
            return new ToolResultDTO(true, "Successfully opened and viewed WhatsApp status for contact '" + contact + "'.",
                Map.of("contact", contact, "status", "VIEWED", "action", "view_contact_status"));
        } catch (Exception e) {
            ensureChatsPanel(page);
            return new ToolResultDTO(false, "Failed to view status for '" + contact + "': " + e.getMessage(),
                Map.of("contact", contact, "action", "view_contact_status"));
        }
    }

    private ToolResultDTO executeStartCall(Page page, String contact, boolean isVideo) {
        String callType = isVideo ? "video call" : "voice call";
        log.info("Executing WhatsApp Web start_{} for '{}'", isVideo ? "video_call" : "audio_call", contact);

        if (contact == null || contact.isBlank()) {
            return new ToolResultDTO(false, "Argument 'contact' is required to start a " + callType + ".", Map.of());
        }

        executeOpenChat(page, contact);

        try {
            page.waitForSelector("#main header", new Page.WaitForSelectorOptions().setTimeout(10000));
            String selector = isVideo
                ? "#main header button[aria-label*='video' i], #main header button:has(span[data-icon='video-call']), #main header span[data-icon='video-call']"
                : "#main header button[aria-label*='voice' i], #main header button[aria-label*='audio' i], #main header button:has(span[data-icon='audio-call']), #main header span[data-icon='audio-call']";

            var callBtn = page.locator(selector).first();
            if (callBtn.count() == 0) {
                return new ToolResultDTO(false, "Call button (" + callType + ") is not available or supported for '" + contact + "' in WhatsApp Web.",
                    Map.of("contact", contact, "action", isVideo ? "start_video_call" : "start_audio_call"));
            }

            callBtn.click();
            page.waitForTimeout(1500);

            return new ToolResultDTO(true, "Successfully initiated WhatsApp " + callType + " to '" + contact + "'.",
                Map.of("contact", contact, "call_type", isVideo ? "VIDEO" : "AUDIO", "status", "RINGING", "action", isVideo ? "start_video_call" : "start_audio_call"));
        } catch (Exception e) {
            return new ToolResultDTO(false, "Failed to start " + callType + " to '" + contact + "': " + e.getMessage(),
                Map.of("contact", contact, "action", isVideo ? "start_video_call" : "start_audio_call"));
        }
    }

    private List<WhatsAppWebStatusItem> parseStatusesJson(String rawJson, int limit) {
        if (rawJson == null || rawJson.isBlank() || rawJson.equals("{}")) {
            return List.of();
        }
        List<WhatsAppWebStatusItem> list = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode itemsNode = root.has("items") ? root.get("items") : root;
            if (itemsNode.isArray()) {
                for (JsonNode n : itemsNode) {
                    String contact = n.has("contact") ? n.get("contact").asText("") : "";
                    String timestamp = n.has("timestamp") ? n.get("timestamp").asText("") : "";
                    boolean unseen = n.has("unseen") && n.get("unseen").asBoolean();
                    if (!contact.isBlank()) {
                        list.add(new WhatsAppWebStatusItem(contact, timestamp, unseen, n.toString()));
                        if (list.size() >= limit) break;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse statuses JSON: {}", e.getMessage());
        }
        return list;
    }
}
