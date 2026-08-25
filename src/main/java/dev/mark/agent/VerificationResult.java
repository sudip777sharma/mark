package dev.mark.agent;

public record VerificationResult(boolean verified, String reason) {
    public static VerificationResult success() { return new VerificationResult(true, "verified"); }
    public static VerificationResult failure(String reason) { return new VerificationResult(false, reason); }
}
