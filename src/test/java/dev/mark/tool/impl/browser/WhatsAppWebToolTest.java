package dev.mark.tool.impl.browser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Keyboard;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import dev.mark.tool.util.BrowserSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhatsAppWebToolTest {

    @Mock
    private BrowserSession browserSession;

    @Mock
    private Page page;

    @Mock
    private Locator locator;

    @Mock
    private Keyboard keyboard;

    private ObjectMapper objectMapper;
    private WhatsAppWebTool whatsAppWebTool;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        whatsAppWebTool = new WhatsAppWebTool(browserSession, objectMapper);

        when(browserSession.getOrCreatePage()).thenReturn(page);
        when(page.url()).thenReturn("https://web.whatsapp.com");
        when(page.locator(anyString())).thenReturn(locator);
        when(page.keyboard()).thenReturn(keyboard);
        when(locator.first()).thenReturn(locator);
    }

    @Test
    void testToolMetadata() {
        assertEquals("whatsapp_web_automation", whatsAppWebTool.name());
        assertTrue(whatsAppWebTool.description().contains("WhatsApp Web"));
        assertTrue(whatsAppWebTool.parameterSchema().containsKey("properties"));
    }

    @Test
    void testOpenSessionLoggedIn() {
        when(locator.count()).thenReturn(1); // #pane-side found

        ToolRequestDTO request = new ToolRequestDTO(UUID.randomUUID(), "whatsapp_web_automation", Map.of(
            "action", "open_session"
        ));

        ToolResultDTO result = whatsAppWebTool.execute(request);
        assertTrue(result.successful());
        assertEquals("LOGGED_IN", result.metadata().get("status"));
        assertTrue(result.observation().contains("ACTIVE"));
    }

    @Test
    void testOpenSessionQrRequired() {
        when(locator.count()).thenReturn(0).thenReturn(1); // pane missing, canvas found

        ToolRequestDTO request = new ToolRequestDTO(UUID.randomUUID(), "whatsapp_web_automation", Map.of(
            "action", "open_session"
        ));

        ToolResultDTO result = whatsAppWebTool.execute(request);
        assertFalse(result.successful());
        assertEquals("QR_SCAN_REQUIRED", result.metadata().get("status"));
        assertTrue(result.observation().contains("QR code"));
    }

    @Test
    void testSendMessagePhoneNumber() {
        ToolRequestDTO request = new ToolRequestDTO(UUID.randomUUID(), "whatsapp_web_automation", Map.of(
            "action", "send_message",
            "contact", "8280901077",
            "message", "Hello from MARK!"
        ));

        ToolResultDTO result = whatsAppWebTool.execute(request);
        assertTrue(result.successful());
        assertEquals("SENT", result.metadata().get("status"));
        assertEquals("8280901077", result.metadata().get("contact"));
        verify(page).navigate(contains("send?phone=918280901077"));
        verify(locator).click();
    }

    @Test
    void testOpenChatPhoneNumber() {
        ToolRequestDTO request = new ToolRequestDTO(UUID.randomUUID(), "whatsapp_web_automation", Map.of(
            "action", "open_chat",
            "contact", "8280901077"
        ));

        ToolResultDTO result = whatsAppWebTool.execute(request);
        assertTrue(result.successful());
        assertEquals("OPENED", result.metadata().get("status"));
        verify(page).navigate(contains("send?phone=918280901077"));
    }

    @Test
    void testParseExtractedJson() {
        String mockJson = """
            [
                {
                    "pre": "[10:14 am, 11/1/2026] 8280901077: ",
                    "text": "Hey there!",
                    "fromMe": false,
                    "status": "Received"
                },
                {
                    "pre": "[10:15 am, 11/1/2026] You: ",
                    "text": "hi",
                    "fromMe": true,
                    "status": "Read"
                }
            ]
            """;

        List<WhatsAppWebTool.WhatsAppWebMessage> messages = whatsAppWebTool.parseExtractedJson(mockJson, "8280901077");
        assertEquals(2, messages.size());

        WhatsAppWebTool.WhatsAppWebMessage incoming = messages.get(0);
        assertFalse(incoming.fromMe());
        assertEquals("8280901077", incoming.sender());
        assertEquals("Hey there!", incoming.text());
        assertEquals("10:14 am, 11/1/2026", incoming.timestamp());
        assertEquals("Received", incoming.status());

        WhatsAppWebTool.WhatsAppWebMessage outgoing = messages.get(1);
        assertTrue(outgoing.fromMe());
        assertEquals("You", outgoing.sender());
        assertEquals("hi", outgoing.text());
        assertEquals("Read", outgoing.status());
    }

    @Test
    void testVerifyMessageSuccess() {
        String mockJson = """
            [
                {
                    "pre": "[10:15 am, 11/1/2026] You: ",
                    "text": "hi",
                    "fromMe": true,
                    "status": "Delivered"
                }
            ]
            """;
        when(page.evaluate(anyString())).thenReturn(mockJson);

        ToolRequestDTO request = new ToolRequestDTO(UUID.randomUUID(), "whatsapp_web_automation", Map.of(
            "action", "verify_message",
            "contact", "8280901077",
            "expected_message", "hi"
        ));

        ToolResultDTO result = whatsAppWebTool.execute(request);
        assertTrue(result.successful());
        assertEquals(Boolean.TRUE, result.metadata().get("verified"));
        assertTrue(result.observation().contains("VERIFICATION PASSED"));
    }

    @Test
    void testVerifyMessageFailure() {
        String mockJson = """
            [
                {
                    "pre": "[10:15 am, 11/1/2026] You: ",
                    "text": "different message",
                    "fromMe": true,
                    "status": "Sent"
                }
            ]
            """;
        when(page.evaluate(anyString())).thenReturn(mockJson);

        ToolRequestDTO request = new ToolRequestDTO(UUID.randomUUID(), "whatsapp_web_automation", Map.of(
            "action", "verify_message",
            "contact", "8280901077",
            "expected_message", "hi"
        ));

        ToolResultDTO result = whatsAppWebTool.execute(request);
        assertFalse(result.successful());
        assertEquals(Boolean.FALSE, result.metadata().get("verified"));
        assertTrue(result.observation().contains("VERIFICATION FAILED"));
    }

    @Test
    void testListUnreadChats() {
        String mockUnreadsJson = """
            [
                {
                    "contact": "Alice",
                    "count": 3,
                    "snippet": "Are we meeting?",
                    "raw": "3 unread messages"
                }
            ]
            """;
        when(page.evaluate(anyString())).thenReturn(mockUnreadsJson);

        ToolRequestDTO request = new ToolRequestDTO(UUID.randomUUID(), "whatsapp_web_automation", Map.of(
            "action", "list_unread_chats"
        ));

        ToolResultDTO result = whatsAppWebTool.execute(request);
        assertTrue(result.successful());
        assertEquals(1, result.metadata().get("unread_chats_count"));
        assertTrue(result.observation().contains("Found 1 chat(s) with unread messages"));
        assertTrue(result.observation().contains("Alice (3 unread messages)"));
    }
}
