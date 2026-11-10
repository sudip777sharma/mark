package dev.mark.skill.impl;

import dev.mark.skill.dto.SkillExecutionResult;
import dev.mark.tool.dto.HumanInterventionRequest;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import dev.mark.tool.impl.browser.WhatsAppWebTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class WhatsAppSendSkillTest {

    private WhatsAppWebTool whatsAppWebTool;
    private WhatsAppSendSkill skill;

    @BeforeEach
    void setUp() {
        whatsAppWebTool = Mockito.mock(WhatsAppWebTool.class);
        skill = new WhatsAppSendSkill(whatsAppWebTool);
    }

    @Test
    void canHandle_matchesVariousPromptPhrasings() {
        assertTrue(skill.canHandle("Send 'hi' to 8280901077 on WhatsApp"));
        assertTrue(skill.canHandle("send hello to Sudeep on whatsapp"));
        assertTrue(skill.canHandle("text 8280901077 on whatsapp: 'meeting at 5'"));
        assertTrue(skill.canHandle("whatsapp 8280901077: 'hello world'"));
        assertTrue(skill.canHandle("Send to 8280901077 on WhatsApp: hi"));

        assertFalse(skill.canHandle("Create a file called summary.txt"));
        assertFalse(skill.canHandle(""));
        assertFalse(skill.canHandle(null));
    }

    @Test
    void extractSlots_extractsContactAndMessageCorrectly() {
        Map<String, Object> slots1 = skill.extractSlots("Send 'hi' to 8280901077 on WhatsApp");
        assertEquals("hi", slots1.get("message"));
        assertEquals("8280901077", slots1.get("contact"));

        Map<String, Object> slots2 = skill.extractSlots("text 8280901077 on whatsapp: 'meeting at 5'");
        assertEquals("8280901077", slots2.get("contact"));
        assertEquals("meeting at 5", slots2.get("message"));

        Map<String, Object> slots3 = skill.extractSlots("whatsapp 8280901077: hello");
        assertEquals("8280901077", slots3.get("contact"));
        assertEquals("hello", slots3.get("message"));
    }

    @Test
    void execute_verifiedSuccessReturnsCompleted() {
        when(whatsAppWebTool.execute(any(ToolRequestDTO.class))).thenReturn(
                new ToolResultDTO(true, "Message sent to 8280901077", Map.of("verified", true, "status", "SENT"))
        );

        SkillExecutionResult result = skill.execute("test-task-1", Map.of("contact", "8280901077", "message", "hi"));

        assertTrue(result.success());
        assertNull(result.intervention());
        assertTrue(result.summary().contains("Message successfully sent and verified"));
        assertEquals(1, result.steps().size());
        assertEquals("L0_DETERMINISTIC", result.steps().get(0).provider());
    }

    @Test
    void execute_requiresQrInterventionReturnsIntervention() {
        HumanInterventionRequest intervention = HumanInterventionRequest.qrScan(
                "WhatsApp Authentication Required",
                "Please scan QR code",
                "mark-workspace/whatsapp_qr.png",
                Map.of("action", "send_message")
        );
        when(whatsAppWebTool.execute(any(ToolRequestDTO.class))).thenReturn(
                ToolResultDTO.intervention(intervention, "QR scan required", Map.of("status", "AUTH_REQUIRED"))
        );

        SkillExecutionResult result = skill.execute("test-task-2", Map.of("contact", "8280901077", "message", "hi"));

        assertFalse(result.success());
        assertNotNull(result.intervention());
        assertEquals("WhatsApp Authentication Required", result.intervention().title());
    }
}
