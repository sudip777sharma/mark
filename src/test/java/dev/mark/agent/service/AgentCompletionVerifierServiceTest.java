package dev.mark.agent.service;

import dev.mark.agent.model.AgentVerificationResultModel;
import dev.mark.agent.model.AgentWorldStateModel;
import dev.mark.llm.dto.LlmMessageDTO;
import dev.mark.llm.dto.LlmToolObservationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentCompletionVerifierServiceTest {

    private AgentCompletionVerifierService verifier;
    private AgentWorldStateModel defaultWorldState;

    @BeforeEach
    void setUp() {
        verifier = new AgentCompletionVerifierService();
        defaultWorldState = AgentWorldStateModel.empty();
        defaultWorldState.setActiveApplication("WhatsApp");
        defaultWorldState.setActiveWindow("WhatsApp Web");
        defaultWorldState.setBrowserUrl("https://web.whatsapp.com");
        defaultWorldState.setBrowserTitle("WhatsApp");
    }

    @Test
    void shouldRejectEmptyCompletion() {
        AgentVerificationResultModel result = verifier.verify("Do something", List.of(), defaultWorldState);
        assertFalse(result.verified());
        assertEquals("Completion is empty", result.reason());
    }

    @Test
    void shouldRejectClarificationQuestion() {
        List<LlmMessageDTO> messages = List.of(
                LlmMessageDTO.assistantCompletion("Could you please clarify what phone number you want to message?")
        );
        AgentVerificationResultModel result = verifier.verify("Send message", messages, defaultWorldState);
        assertFalse(result.verified());
        assertTrue(result.reason().contains("clarification"));
    }

    @Test
    void shouldRejectWhatsAppGoalWithoutAnyTools() {
        List<LlmMessageDTO> messages = List.of(
                LlmMessageDTO.assistantCompletion("I have sent the message to your contact on WhatsApp.")
        );
        AgentVerificationResultModel result = verifier.verify("Send message to mom on WhatsApp", messages, defaultWorldState);
        assertFalse(result.verified());
        assertTrue(result.reason().contains("requires WhatsApp messaging/verification"));
    }

    @Test
    void shouldRejectWhatsAppGoalWithOnlyOpenSessionTool() {
        // Classic bug regression: open_session succeeded, but no message was sent!
        LlmToolObservationDTO openSessionObs = new LlmToolObservationDTO(
                "call-1", "whatsapp_web_automation", true,
                "WhatsApp Web session is ACTIVE and logged in.",
                Map.of("action", "open_session", "status", "LOGGED_IN")
        );
        List<LlmMessageDTO> messages = List.of(
                LlmMessageDTO.toolObservation(openSessionObs),
                LlmMessageDTO.assistantCompletion("WhatsApp session opened. Done!")
        );

        AgentVerificationResultModel result = verifier.verify("Send message to mom on WhatsApp", messages, defaultWorldState);
        assertFalse(result.verified());
        assertTrue(result.reason().contains("requires WhatsApp messaging/verification"));
    }

    @Test
    void shouldRejectWhatsAppGoalWhenSendIsUnverified() {
        LlmToolObservationDTO sendObs = new LlmToolObservationDTO(
                "call-2", "whatsapp_web_automation", false,
                "Message send unverified: Message was not found in outgoing chat DOM bubbles.",
                Map.of("action", "send_message", "status", "SENT", "verified", false, "verification_status", "UNVERIFIED")
        );
        List<LlmMessageDTO> messages = List.of(
                LlmMessageDTO.toolObservation(sendObs),
                LlmMessageDTO.assistantCompletion("I tried sending the message.")
        );

        AgentVerificationResultModel result = verifier.verify("Send 'hi' to 8280901077 on WhatsApp", messages, defaultWorldState);
        assertFalse(result.verified());
        assertTrue(result.reason().contains("requires WhatsApp messaging/verification"));
    }

    @Test
    void shouldAcceptWhatsAppGoalWhenSendIsVerified() {
        LlmToolObservationDTO sendObs = new LlmToolObservationDTO(
                "call-2", "whatsapp_web_automation", true,
                "Successfully sent WhatsApp message to '8280901077' and verified delivery in chat DOM: \"hi\"",
                Map.of("action", "send_message", "status", "SENT", "verified", true, "verification_status", "VERIFIED")
        );
        List<LlmMessageDTO> messages = List.of(
                LlmMessageDTO.toolObservation(sendObs),
                LlmMessageDTO.assistantCompletion("Message 'hi' has been sent and verified in WhatsApp chat history.")
        );

        AgentVerificationResultModel result = verifier.verify("Send 'hi' to 8280901077 on WhatsApp", messages, defaultWorldState);
        assertTrue(result.verified());
    }

    @Test
    void shouldRejectSayHiOnWhatsAppBypassingToolEvidence() {
        // Bug regression: "say hi" prefix should NOT bypass tool evidence when WhatsApp is targeted
        List<LlmMessageDTO> messages = List.of(
                LlmMessageDTO.assistantCompletion("Hi! I have greeted your contact.")
        );

        AgentVerificationResultModel result = verifier.verify("Say hi to mom on WhatsApp", messages, defaultWorldState);
        assertFalse(result.verified());
        assertTrue(result.reason().contains("requires WhatsApp messaging/verification"));
    }

    @Test
    void shouldNotTreatHistoryAsGreetingPrefix() {
        // Bug regression: "history" starts with "hi", but is not a greeting
        List<LlmMessageDTO> messages = List.of(
                LlmMessageDTO.assistantCompletion("I will do something.")
        );

        AgentVerificationResultModel result = verifier.verify("History file analysis", messages, defaultWorldState);
        assertFalse(result.verified());
    }

    @Test
    void shouldAcceptDirectInformationalQuery() {
        List<LlmMessageDTO> messages = List.of(
                LlmMessageDTO.assistantCompletion("The capital of France is Paris.")
        );

        AgentVerificationResultModel result = verifier.verify("What is the capital of France?", messages, defaultWorldState);
        assertTrue(result.verified());
    }

    @Test
    void shouldRejectFileMutationGoalWithoutFileTool() {
        List<LlmMessageDTO> messages = List.of(
                LlmMessageDTO.assistantCompletion("I have noted down your plan in a file.")
        );

        AgentVerificationResultModel result = verifier.verify("Create a file named summary.txt with notes", messages, defaultWorldState);
        assertFalse(result.verified());
        assertTrue(result.reason().contains("requires creating or modifying a file"));
    }

    @Test
    void shouldAcceptFileMutationGoalWithSuccessfulWriteFile() {
        LlmToolObservationDTO fileObs = new LlmToolObservationDTO(
                "call-3", "write_file", true,
                "File created successfully: summary.txt",
                Map.of("path", "summary.txt")
        );
        List<LlmMessageDTO> messages = List.of(
                LlmMessageDTO.toolObservation(fileObs),
                LlmMessageDTO.assistantCompletion("File summary.txt has been written.")
        );

        AgentVerificationResultModel result = verifier.verify("Create a file named summary.txt with notes", messages, defaultWorldState);
        assertTrue(result.verified());
    }

    @Test
    void shouldRejectNonConsequentialInspectionToolAlone() {
        LlmToolObservationDTO inspectObs = new LlmToolObservationDTO(
                "call-4", "inspect_ui", true,
                "Window elements listed.",
                Map.of()
        );
        List<LlmMessageDTO> messages = List.of(
                LlmMessageDTO.toolObservation(inspectObs),
                LlmMessageDTO.assistantCompletion("I inspected the windows.")
        );

        AgentVerificationResultModel result = verifier.verify("Calculate monthly expenses in Excel", messages, defaultWorldState);
        assertFalse(result.verified());
        assertTrue(result.reason().contains("Non-consequential inspection tools alone do not verify task completion"));
    }
}
