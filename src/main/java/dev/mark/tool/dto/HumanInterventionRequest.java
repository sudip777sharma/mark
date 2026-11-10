package dev.mark.tool.dto;

import java.util.Map;

/**
 * Universal, protocol-driven human intervention request.
 * Allows tools and skills to pause execution and solicit user action (QR scan, OTP, confirmation, captcha)
 * without hardcoded strings or extra LLM reasoning cycles.
 */
public record HumanInterventionRequest(
    InterventionType type,
    String title,
    String prompt,
    String mediaUri,
    Map<String, Object> payload
) {
    public HumanInterventionRequest {
        payload = payload != null ? Map.copyOf(payload) : Map.of();
    }

    public static HumanInterventionRequest qrScan(String title, String prompt, String mediaUri, Map<String, Object> payload) {
        return new HumanInterventionRequest(InterventionType.QR_SCAN, title, prompt, mediaUri, payload);
    }

    public static HumanInterventionRequest otp(String title, String prompt, Map<String, Object> payload) {
        return new HumanInterventionRequest(InterventionType.OTP_2FA, title, prompt, null, payload);
    }

    public static HumanInterventionRequest confirmation(String title, String prompt, Map<String, Object> payload) {
        return new HumanInterventionRequest(InterventionType.CONFIRMATION, title, prompt, null, payload);
    }

    public static HumanInterventionRequest captcha(String title, String prompt, String mediaUri, Map<String, Object> payload) {
        return new HumanInterventionRequest(InterventionType.CAPTCHA, title, prompt, mediaUri, payload);
    }

    public static HumanInterventionRequest clarification(String title, String prompt, Map<String, Object> payload) {
        return new HumanInterventionRequest(InterventionType.CLARIFICATION, title, prompt, null, payload);
    }
}
