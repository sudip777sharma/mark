package dev.mark.tool.dto;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HumanInterventionRequestTest {

    @Test
    void qrScan_createsValidIntervention() {
        HumanInterventionRequest req = HumanInterventionRequest.qrScan(
                "WhatsApp Auth",
                "Scan with phone",
                "mark-workspace/qr.png",
                Map.of("session", "active")
        );
        assertEquals(InterventionType.QR_SCAN, req.type());
        assertEquals("WhatsApp Auth", req.title());
        assertEquals("Scan with phone", req.prompt());
        assertEquals("mark-workspace/qr.png", req.mediaUri());
        assertEquals("active", req.payload().get("session"));
    }

    @Test
    void otp_createsValidIntervention() {
        HumanInterventionRequest req = HumanInterventionRequest.otp(
                "Enter OTP",
                "Please enter the 6-digit code sent to your phone",
                Map.of("provider", "IRCTC")
        );
        assertEquals(InterventionType.OTP_2FA, req.type());
        assertNull(req.mediaUri());
        assertEquals("IRCTC", req.payload().get("provider"));
    }

    @Test
    void confirmation_createsValidIntervention() {
        HumanInterventionRequest req = HumanInterventionRequest.confirmation(
                "Confirm Delete",
                "Are you sure you want to delete file 'data.csv'?",
                Map.of("target", "data.csv")
        );
        assertEquals(InterventionType.CONFIRMATION, req.type());
    }

    @Test
    void toolResultDTO_supportsInterventionContract() {
        HumanInterventionRequest req = HumanInterventionRequest.clarification("Clarification", "Which contact?", Map.of());
        ToolResultDTO result = ToolResultDTO.intervention(req, "Clarification needed", Map.of("status", "PAUSED"));

        assertFalse(result.successful());
        assertEquals("Clarification needed", result.observation());
        assertNotNull(result.interventionRequest());
        assertEquals(InterventionType.CLARIFICATION, result.interventionRequest().type());
    }
}
