package dev.mark.tool.impl.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WhatsAppToolTest {

    private DesktopAutomationTool desktopAutomation;
    private ObjectMapper objectMapper;
    private WhatsAppTool whatsAppTool;

    @BeforeEach
    void setUp() {
        desktopAutomation = mock(DesktopAutomationTool.class);
        objectMapper = new ObjectMapper();
        whatsAppTool = new WhatsAppTool(desktopAutomation, objectMapper);
    }

    @Test
    void testNameAndSchema() {
        assertEquals("whatsapp_automation", whatsAppTool.name());
        assertNotNull(whatsAppTool.description());
        Map<String, Object> schema = whatsAppTool.parameterSchema();
        assertTrue(schema.containsKey("properties"));
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("action"));
        assertTrue(props.containsKey("contact"));
        assertTrue(props.containsKey("message"));
        assertTrue(props.containsKey("expected_message"));
        assertTrue(props.containsKey("limit"));
    }

    @Test
    void testValidationMissingActionOrContact() {
        ToolResultDTO resNoAction = whatsAppTool.execute(new ToolRequestDTO(UUID.randomUUID(), "whatsapp_automation", Map.of()));
        assertFalse(resNoAction.successful());
        assertTrue(resNoAction.observation().contains("action"));

        ToolResultDTO resNoContact = whatsAppTool.execute(new ToolRequestDTO(UUID.randomUUID(), "whatsapp_automation", Map.of("action", "send_message")));
        assertFalse(resNoContact.successful());
        assertTrue(resNoContact.observation().contains("contact"));

        ToolResultDTO resNoMessage = whatsAppTool.execute(new ToolRequestDTO(UUID.randomUUID(), "whatsapp_automation", Map.of("action", "send_message", "contact", "John")));
        assertFalse(resNoMessage.successful());
        assertTrue(resNoMessage.observation().contains("message"));
    }

    @Test
    void testSendMessageSuccess() {
        when(desktopAutomation.ensureFocus(anyString())).thenReturn(ToolResultDTO.success("Focus verified"));

        ToolRequestDTO request = new ToolRequestDTO(UUID.randomUUID(), "whatsapp_automation", Map.of(
            "action", "send_message",
            "contact", "Alice",
            "message", "Hello Alice!"
        ));

        ToolResultDTO result = whatsAppTool.execute(request);
        assertTrue(result.successful());
        assertTrue(result.observation().contains("Successfully sent WhatsApp message to 'Alice'"));
        assertEquals("SENT", result.metadata().get("status"));
        assertEquals("Alice", result.metadata().get("contact"));

        verify(desktopAutomation, atLeastOnce()).pressKeyCombination("CTRL+F");
        verify(desktopAutomation, atLeastOnce()).pressKeyCombination("CTRL+A");
        verify(desktopAutomation, atLeastOnce()).pressKeyCombination("BACKSPACE");
        verify(desktopAutomation, atLeastOnce()).pressKeyCombination("CTRL+V");
        verify(desktopAutomation, atLeastOnce()).pressKeyCombination("DOWN");
        verify(desktopAutomation, atLeastOnce()).pressKeyCombination("ENTER");
    }

    @Test
    void testOpenChatSuccess() {
        when(desktopAutomation.ensureFocus(anyString())).thenReturn(ToolResultDTO.success("Focus verified"));

        ToolRequestDTO request = new ToolRequestDTO(UUID.randomUUID(), "whatsapp_automation", Map.of(
            "action", "open_chat",
            "contact", "Bob"
        ));

        ToolResultDTO result = whatsAppTool.execute(request);
        assertTrue(result.successful());
        assertTrue(result.observation().contains("Successfully opened WhatsApp chat with 'Bob'"));
        assertEquals("OPENED", result.metadata().get("status"));
    }

    @Test
    void testReadChatWithVerificationSuccess() throws Exception {
        when(desktopAutomation.ensureFocus(anyString())).thenReturn(ToolResultDTO.success("Focus verified"));

        String mockJson = "[{\"id\":\"msg_1\",\"name\":\"Alice, Are we meeting today?, 3:20 PM\"},{\"id\":\"msg_2\",\"name\":\"You, hi, 3:24 PM, Sent\"}]";
        when(desktopAutomation.runPowershell(anyString())).thenReturn(mockJson);

        ToolRequestDTO request = new ToolRequestDTO(UUID.randomUUID(), "whatsapp_automation", Map.of(
            "action", "read_chat",
            "contact", "Alice",
            "expected_message", "hi"
        ));

        ToolResultDTO result = whatsAppTool.execute(request);
        assertTrue(result.successful());
        assertTrue(result.observation().contains("VERIFICATION PASSED"));
        assertTrue(result.observation().contains("You: hi"));
        assertEquals(Boolean.TRUE, result.metadata().get("verified"));
        assertEquals(2, result.metadata().get("count"));
    }

    @Test
    void testReadChatWithVerificationFailure() throws Exception {
        when(desktopAutomation.ensureFocus(anyString())).thenReturn(ToolResultDTO.success("Focus verified"));

        String mockJson = "[{\"id\":\"msg_1\",\"name\":\"Alice, Are we meeting today?, 3:20 PM\"}]";
        when(desktopAutomation.runPowershell(anyString())).thenReturn(mockJson);

        ToolRequestDTO request = new ToolRequestDTO(UUID.randomUUID(), "whatsapp_automation", Map.of(
            "action", "read_chat",
            "expected_message", "hello from future"
        ));

        ToolResultDTO result = whatsAppTool.execute(request);
        assertFalse(result.successful());
        assertTrue(result.observation().contains("VERIFICATION FAILED"));
        assertEquals(Boolean.FALSE, result.metadata().get("verified"));
    }

    @Test
    void testParseMessageElement() {
        WhatsAppTool.WhatsAppMessage outgoing = whatsAppTool.parseMessageElement("You, hi, 3:24 PM, Sent", "8280901077");
        assertTrue(outgoing.fromMe());
        assertEquals("You", outgoing.sender());
        assertEquals("hi", outgoing.text());
        assertEquals("3:24 PM", outgoing.timestamp());
        assertEquals("Sent", outgoing.status());

        WhatsAppTool.WhatsAppMessage incoming = whatsAppTool.parseMessageElement("8280901077, sounds good!, 3:25 PM", "8280901077");
        assertFalse(incoming.fromMe());
        assertEquals("8280901077", incoming.sender());
        assertEquals("sounds good!", incoming.text());
        assertEquals("3:25 PM", incoming.timestamp());
        assertEquals("Received", incoming.status());

        // Group chat message from another member
        WhatsAppTool.WhatsAppMessage groupMember = whatsAppTool.parseMessageElement("Himabindu Infosys, Okay, 1:49 AM", "Team Group");
        assertFalse(groupMember.fromMe());
        assertEquals("Himabindu Infosys", groupMember.sender());
        assertEquals("Okay", groupMember.text());
        assertEquals("1:49 AM", groupMember.timestamp());
    }

    @Test
    void testOpenChatPhoneNumber() {
        when(desktopAutomation.ensureFocus(anyString())).thenReturn(ToolResultDTO.success("Focus verified"));

        ToolRequestDTO request = new ToolRequestDTO(UUID.randomUUID(), "whatsapp_automation", Map.of(
            "action", "open_chat",
            "contact", "8280901077"
        ));

        ToolResultDTO result = whatsAppTool.execute(request);
        assertTrue(result.successful());
        assertTrue(result.observation().contains("Successfully opened WhatsApp chat with '8280901077'"));
        assertEquals("OPENED", result.metadata().get("status"));
    }

    @Test
    void testVerifyMessageAction() throws Exception {
        when(desktopAutomation.ensureFocus(anyString())).thenReturn(ToolResultDTO.success("Focus verified"));

        String mockJson = "[{\"id\":\"msg_1\",\"name\":\"Alice, Are we meeting today?, 3:20 PM\"},{\"id\":\"msg_2\",\"name\":\"You, hi, 3:24 PM, Sent\"}]";
        when(desktopAutomation.runPowershell(anyString())).thenReturn(mockJson);

        ToolRequestDTO requestPass = new ToolRequestDTO(UUID.randomUUID(), "whatsapp_automation", Map.of(
            "action", "verify_message",
            "contact", "Alice",
            "expected_message", "hi"
        ));
        ToolResultDTO resPass = whatsAppTool.execute(requestPass);
        assertTrue(resPass.successful());
        assertTrue(resPass.observation().contains("VERIFICATION PASSED"));
        assertEquals(Boolean.TRUE, resPass.metadata().get("verified"));

        ToolRequestDTO requestFail = new ToolRequestDTO(UUID.randomUUID(), "whatsapp_automation", Map.of(
            "action", "verify_message",
            "contact", "Alice",
            "expected_message", "nonexistent message"
        ));
        ToolResultDTO resFail = whatsAppTool.execute(requestFail);
        assertFalse(resFail.successful());
        assertTrue(resFail.observation().contains("VERIFICATION FAILED"));
        assertEquals(Boolean.FALSE, resFail.metadata().get("verified"));

        // Case 3: Plain text bubble like "hi" (WinUI Text/Custom control)
        String plainBubbleJson = "[{\"id\":\"msg_3\",\"name\":\"hi\"}]";
        when(desktopAutomation.runPowershell(anyString())).thenReturn(plainBubbleJson);
        ToolRequestDTO requestPlain = new ToolRequestDTO(UUID.randomUUID(), "whatsapp_automation", Map.of(
            "action", "verify_message",
            "contact", "8280901077",
            "expected_message", "hi"
        ));
        ToolResultDTO resPlain = whatsAppTool.execute(requestPlain);
        assertTrue(resPlain.successful());
        assertTrue(resPlain.observation().contains("VERIFICATION PASSED"));
        assertEquals(Boolean.TRUE, resPlain.metadata().get("verified"));

        // Case 4: Single JSON object without array brackets (PowerShell count == 1 serialization)
        String singleObjectJson = "{\"id\":\"msg_single\",\"name\":\"You, hi, 3:24 PM, Sent\"}";
        when(desktopAutomation.runPowershell(anyString())).thenReturn(singleObjectJson);
        ToolResultDTO resSingle = whatsAppTool.execute(requestPlain);
        assertTrue(resSingle.successful());
        assertTrue(resSingle.observation().contains("VERIFICATION PASSED"));
        assertEquals(Boolean.TRUE, resSingle.metadata().get("verified"));
    }

    @Test
    void testListUnreadChats() throws Exception {
        when(desktopAutomation.ensureFocus(anyString())).thenReturn(ToolResultDTO.success("Focus verified"));

        // Case 1: No unreads
        when(desktopAutomation.runPowershell(anyString())).thenReturn("[]");
        ToolRequestDTO emptyRequest = new ToolRequestDTO(UUID.randomUUID(), "whatsapp_automation", Map.of(
            "action", "list_unread_chats"
        ));
        ToolResultDTO emptyRes = whatsAppTool.execute(emptyRequest);
        assertTrue(emptyRes.successful());
        assertTrue(emptyRes.observation().contains("No unread messages found"));
        assertEquals(0, emptyRes.metadata().get("unread_chats_count"));

        // Case 2: Unreads present (JSON Array)
        String mockUnreadsJson = "[{\"id\":\"item_1\",\"name\":\"Alice, Hello, 10:15 AM, 2 unread messages\"}]";
        when(desktopAutomation.runPowershell(anyString())).thenReturn(mockUnreadsJson);
        ToolResultDTO unreadRes = whatsAppTool.execute(emptyRequest);
        assertTrue(unreadRes.successful());
        assertTrue(unreadRes.observation().contains("Found 1 chat(s) with unread messages"));
        assertEquals(1, unreadRes.metadata().get("unread_chats_count"));

        // Case 3: Single unread object without array brackets
        String singleUnreadObjectJson = "{\"id\":\"item_single\",\"name\":\"Alice, Hello, 10:15 AM, 2 unread messages\"}";
        when(desktopAutomation.runPowershell(anyString())).thenReturn(singleUnreadObjectJson);
        ToolResultDTO singleUnreadRes = whatsAppTool.execute(emptyRequest);
        assertTrue(singleUnreadRes.successful());
        assertTrue(singleUnreadRes.observation().contains("Found 1 chat(s) with unread messages"));
        assertEquals(1, singleUnreadRes.metadata().get("unread_chats_count"));
    }
}
