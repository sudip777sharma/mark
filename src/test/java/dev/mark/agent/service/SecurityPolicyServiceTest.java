package dev.mark.agent.service;

import dev.mark.tool.contract.IdempotencyPolicy;
import dev.mark.tool.contract.ToolContract;
import dev.mark.tool.contract.ToolParameterSchema;
import dev.mark.tool.contract.ToolRiskClassification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SecurityPolicyServiceTest {

    private SecurityPolicyService securityPolicyService;

    @BeforeEach
    void setUp() {
        securityPolicyService = new SecurityPolicyService();
    }

    @Test
    void blocksWorkspaceRootDeletion() {
        ToolContract deleteContract = ToolContract.builder("delete_file")
                .risk(ToolRiskClassification.HIGH_RISK_DESTRUCTIVE)
                .parameter(ToolParameterSchema.requiredString("path", "File path"))
                .build();

        var evalDot = securityPolicyService.evaluate(deleteContract, Map.of("path", "."));
        assertFalse(evalDot.allowed());
        assertTrue(evalDot.reason().contains("workspace root is blocked"));

        var evalSlash = securityPolicyService.evaluate(deleteContract, Map.of("path", "/"));
        assertFalse(evalSlash.allowed());
        assertTrue(evalSlash.reason().contains("workspace root is blocked"));
    }

    @Test
    void requiresApprovalForNormalFileDeletion() {
        ToolContract deleteContract = ToolContract.builder("delete_file")
                .risk(ToolRiskClassification.HIGH_RISK_DESTRUCTIVE)
                .parameter(ToolParameterSchema.requiredString("path", "File path"))
                .build();

        var eval = securityPolicyService.evaluate(deleteContract, Map.of("path", "temp_logs.txt"));
        assertTrue(eval.allowed());
        assertTrue(eval.requiresApproval());
        assertNotNull(eval.approvalRequest());
        assertTrue(eval.approvalRequest().prompt().contains("temp_logs.txt"));
    }

    @Test
    void blocksDestructiveSystemCommands() {
        ToolContract cmdContract = ToolContract.builder("execute_command")
                .risk(ToolRiskClassification.SYSTEM_CONSEQUENTIAL)
                .idempotency(IdempotencyPolicy.NON_IDEMPOTENT)
                .parameter(ToolParameterSchema.requiredString("command", "Command"))
                .build();

        var evalFormat = securityPolicyService.evaluate(cmdContract, Map.of("command", "format C: /y"));
        assertFalse(evalFormat.allowed());
        assertTrue(evalFormat.reason().contains("destructive system operation detected"));

        var evalDiskpart = securityPolicyService.evaluate(cmdContract, Map.of("command", "diskpart /s clean.txt"));
        assertFalse(evalDiskpart.allowed());

        var evalForkBomb = securityPolicyService.evaluate(cmdContract, Map.of("command", ":(){ :|:& };:"));
        assertFalse(evalForkBomb.allowed());
    }

    @Test
    void permitsSafeCommands() {
        ToolContract cmdContract = ToolContract.builder("execute_command")
                .risk(ToolRiskClassification.SYSTEM_CONSEQUENTIAL)
                .idempotency(IdempotencyPolicy.NON_IDEMPOTENT)
                .parameter(ToolParameterSchema.requiredString("command", "Command"))
                .build();

        var evalDir = securityPolicyService.evaluate(cmdContract, Map.of("command", "dir /b"));
        assertTrue(evalDir.allowed());
        assertFalse(evalDir.requiresApproval());
    }
}
