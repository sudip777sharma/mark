package dev.mark.tool.dto;

/**
 * Standardized categories of human-in-the-loop (HITL) interventions.
 * Enables specialized frontend rendering (e.g. QR code viewer, OTP form, confirmation dialog).
 */
public enum InterventionType {
    /** QR code scan required on a paired device (e.g. WhatsApp Web). */
    QR_SCAN,
    /** One-Time Password / 2FA code entry. */
    OTP_2FA,
    /** Explicit user confirmation before high-stakes or irreversible actions. */
    CONFIRMATION,
    /** Captcha challenge resolution. */
    CAPTCHA,
    /** Ambiguity resolution or clarification question. */
    CLARIFICATION
}
