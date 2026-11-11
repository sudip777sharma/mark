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

class WhatsAppStatusSkillTest {

    private WhatsAppWebTool whatsAppWebTool;
    private WhatsAppStatusSkill skill;

    @BeforeEach
    void setUp() {
        whatsAppWebTool = Mockito.mock(WhatsAppWebTool.class);
        skill = new WhatsAppStatusSkill(whatsAppWebTool);
    }

    @Test
    void canHandle_matchesVariousStatusPhrasings() {
        assertTrue(skill.canHandle("put a whatsapp status happy diwali from mark with amazing caption"));
        assertTrue(skill.canHandle("post a whatsapp status happy diwali"));
        assertTrue(skill.canHandle("set a whatsapp status 'excited for new release'"));
        assertTrue(skill.canHandle("update my whatsapp status hello world"));
        assertTrue(skill.canHandle("post status on whatsapp happy new year"));
        assertTrue(skill.canHandle("put status on whatsapp good morning"));
        assertTrue(skill.canHandle("whatsapp status: loving the coding session"));

        assertFalse(skill.canHandle("Send 'hi' to 8280901077 on WhatsApp"));
        assertFalse(skill.canHandle("Create a file called notes.txt"));
        assertFalse(skill.canHandle(""));
        assertFalse(skill.canHandle(null));
    }

    @Test
    void extractSlots_extractsMessageCorrectly() {
        Map<String, Object> slots1 = skill.extractSlots("put a whatsapp status happy diwali from mark with amazing caption");
        assertEquals("happy diwali from mark with amazing caption", slots1.get("message"));

        Map<String, Object> slots2 = skill.extractSlots("set a whatsapp status 'celebrating diwali'");
        assertEquals("celebrating diwali", slots2.get("message"));

        Map<String, Object> slots3 = skill.extractSlots("whatsapp status: hello world");
        assertEquals("hello world", slots3.get("message"));
    }

    @Test
    void execute_successfulStatusReturnsCompleted() {
        when(whatsAppWebTool.execute(any(ToolRequestDTO.class))).thenReturn(
                new ToolResultDTO(true, "Successfully posted new WhatsApp status update: happy diwali", Map.of("verified", true, "status", "POSTED"))
        );

        SkillExecutionResult result = skill.execute("task-status-1", Map.of("message", "happy diwali"));

        assertTrue(result.success());
        assertNull(result.intervention());
        assertTrue(result.summary().contains("Status successfully posted to WhatsApp"));
        assertEquals(1, result.steps().size());
        assertEquals("L0_DETERMINISTIC", result.steps().get(0).provider());
    }

    @Test
    void execute_requiresQrInterventionReturnsIntervention() {
        HumanInterventionRequest intervention = HumanInterventionRequest.qrScan(
                "WhatsApp Authentication Required",
                "Please scan QR code",
                "mark-workspace/whatsapp_qr.png",
                Map.of("action", "post_status")
        );
        when(whatsAppWebTool.execute(any(ToolRequestDTO.class))).thenReturn(
                ToolResultDTO.intervention(intervention, "QR scan required", Map.of("status", "AUTH_REQUIRED"))
        );

        SkillExecutionResult result = skill.execute("task-status-2", Map.of("message", "happy diwali"));

        assertFalse(result.success());
        assertNotNull(result.intervention());
        assertEquals("WhatsApp Authentication Required", result.intervention().title());
    }
}
