package dev.mark.tool.contract;

import dev.mark.agent.service.AgentSafetyService;
import dev.mark.agent.service.AgentToolExecutorService;
import dev.mark.agent.service.SecurityPolicyService;
import dev.mark.llm.dto.LlmToolCallDTO;
import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import dev.mark.tool.registry.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ToolContractExecutionTest {

    private ToolRegistry toolRegistry;
    private ToolContractRegistry contractRegistry;
    private SecurityPolicyService securityPolicyService;
    private AgentSafetyService legacySafetyService;
    private AgentToolExecutorService executorService;

    @BeforeEach
    void setUp() {
        toolRegistry = mock(ToolRegistry.class);
        contractRegistry = new ToolContractRegistry(List.of());
        securityPolicyService = new SecurityPolicyService();
        legacySafetyService = mock(AgentSafetyService.class);
        when(legacySafetyService.isDangerousToolCall(any())).thenReturn(false);

        executorService = new AgentToolExecutorService(
                toolRegistry,
                contractRegistry,
                securityPolicyService,
                legacySafetyService
        );
    }

    @Test
    void transitionsToValidationFailedWhenArgumentsViolateContract() {
        ToolContract contract = ToolContract.builder("test_tool")
                .parameter(ToolParameterSchema.requiredString("file_path", "Path to file"))
                .allowUnknownParameters(false)
                .build();
        contractRegistry.registerContract(contract);

        Tool mockTool = mock(Tool.class);
        when(mockTool.name()).thenReturn("test_tool");
        when(toolRegistry.getTool("test_tool")).thenReturn(Optional.of(mockTool));

        LlmToolCallDTO call = new LlmToolCallDTO("call-1", "test_tool", Map.of("wrong_param", "data.txt"));

        ToolResultDTO result = executorService.execute("00000000-0000-0000-0000-000000000001", call);

        assertFalse(result.successful());
        assertEquals("VALIDATION_FAILED", result.metadata().get("lifecycle_state"));
        assertTrue(result.observation().contains("Parameter validation failed"));
        verify(mockTool, never()).execute(any());
    }

    @Test
    void transitionsToPreconditionFailedWhenPreconditionCheckFails() {
        ToolContract contract = ToolContract.builder("protected_tool")
                .parameter(ToolParameterSchema.requiredString("account_id", "Account"))
                .precondition(req -> PreconditionCheck.PreconditionResult.fail("Database connection is offline", Map.of("ping", false)))
                .build();
        contractRegistry.registerContract(contract);

        Tool mockTool = mock(Tool.class);
        when(mockTool.name()).thenReturn("protected_tool");
        when(toolRegistry.getTool("protected_tool")).thenReturn(Optional.of(mockTool));

        LlmToolCallDTO call = new LlmToolCallDTO("call-2", "protected_tool", Map.of("account_id", "123"));

        ToolResultDTO result = executorService.execute("00000000-0000-0000-0000-000000000002", call);

        assertFalse(result.successful());
        assertEquals("PRECONDITION_FAILED", result.metadata().get("lifecycle_state"));
        assertTrue(result.observation().contains("Precondition failed: Database connection is offline"));
        verify(mockTool, never()).execute(any());
    }

    @Test
    void transitionsToAuthorizationRequiredWhenSecurityPolicyRequiresApproval() {
        ToolContract contract = ToolContract.builder("delete_file")
                .risk(ToolRiskClassification.HIGH_RISK_DESTRUCTIVE)
                .parameter(ToolParameterSchema.requiredString("path", "File path"))
                .build();
        contractRegistry.registerContract(contract);

        Tool mockTool = mock(Tool.class);
        when(mockTool.name()).thenReturn("delete_file");
        when(toolRegistry.getTool("delete_file")).thenReturn(Optional.of(mockTool));

        LlmToolCallDTO call = new LlmToolCallDTO("call-3", "delete_file", Map.of("path", "critical_db.sqlite"));

        ToolResultDTO result = executorService.execute("00000000-0000-0000-0000-000000000003", call);

        assertFalse(result.successful());
        assertEquals("AUTHORIZATION_REQUIRED", result.metadata().get("lifecycle_state"));
        assertNotNull(result.interventionRequest());
        assertTrue(result.interventionRequest().prompt().contains("critical_db.sqlite"));
        verify(mockTool, never()).execute(any());
    }

    @Test
    void transitionsToVerificationFailedWhenPostconditionFails() {
        ToolContract contract = ToolContract.builder("write_doc")
                .parameter(ToolParameterSchema.requiredString("path", "Path"))
                .postcondition((req, res) -> PostconditionCheck.PostconditionResult.fail("File was not found on disk after write", Map.of("exists", false)))
                .build();
        contractRegistry.registerContract(contract);

        Tool mockTool = mock(Tool.class);
        when(mockTool.name()).thenReturn("write_doc");
        when(toolRegistry.getTool("write_doc")).thenReturn(Optional.of(mockTool));
        when(mockTool.execute(any(ToolRequestDTO.class))).thenReturn(ToolResultDTO.success("Write completed"));

        LlmToolCallDTO call = new LlmToolCallDTO("call-4", "write_doc", Map.of("path", "output.txt"));

        ToolResultDTO result = executorService.execute("00000000-0000-0000-0000-000000000004", call);

        assertFalse(result.successful());
        assertEquals("VERIFICATION_FAILED", result.metadata().get("lifecycle_state"));
        assertEquals(false, result.metadata().get("verified"));
        assertTrue(result.observation().contains("Postcondition verification failed"));
    }

    @Test
    void transitionsToPostconditionsVerifiedWhenPostconditionPasses() {
        ToolContract contract = ToolContract.builder("write_doc")
                .parameter(ToolParameterSchema.requiredString("path", "Path"))
                .postcondition((req, res) -> PostconditionCheck.PostconditionResult.pass("File verified on disk", Map.of("bytes", 1024)))
                .build();
        contractRegistry.registerContract(contract);

        Tool mockTool = mock(Tool.class);
        when(mockTool.name()).thenReturn("write_doc");
        when(toolRegistry.getTool("write_doc")).thenReturn(Optional.of(mockTool));
        when(mockTool.execute(any(ToolRequestDTO.class))).thenReturn(ToolResultDTO.success("Write completed"));

        LlmToolCallDTO call = new LlmToolCallDTO("call-5", "write_doc", Map.of("path", "output.txt"));

        ToolResultDTO result = executorService.execute("00000000-0000-0000-0000-000000000005", call);

        assertTrue(result.successful());
        assertEquals("POSTCONDITIONS_VERIFIED", result.metadata().get("lifecycle_state"));
        assertEquals(true, result.metadata().get("verified"));
        assertEquals(1024, result.metadata().get("bytes"));
    }
}
