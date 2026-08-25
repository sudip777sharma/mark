package dev.mark.task;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import dev.mark.llm.LlmProvider;
import dev.mark.llm.LlmResponse;
import dev.mark.llm.PlanResponse;
import dev.mark.llm.LlmToolCall;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"mark.llm.active-provider=local", "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class TaskControllerIntegrationTest {
    @Autowired MockMvc mockMvc;
    @MockBean LlmProvider localLlmProvider;
    @Test void createsAndCompletesLlmSelectedTask() throws Exception {
        when(localLlmProvider.plan(any())).thenReturn(new PlanResponse(List.of("step 1")));
        when(localLlmProvider.complete(any())).thenReturn(
                new LlmResponse("", "local", false, List.of(new LlmToolCall("call_1", "echo", Map.of("message", "Check status")))),
                new LlmResponse("The message was echoed.", "local", false, List.of()));
        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("{\"goal\":\"Check status\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PLANNING"));
    }
}
