package dev.mark.tool.impl.system;

import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskCompleteToolTest {

    private TaskCompleteTool tool;

    @BeforeEach
    void setUp() {
        tool = new TaskCompleteTool();
    }

    private ToolRequestDTO req(Map<String, Object> args) {
        return new ToolRequestDTO(UUID.randomUUID(), "task_complete", args);
    }

    @Test
    void execute_successfulSummary_returnsSuccess() {
        ToolResultDTO result = tool.execute(req(Map.of("summary", "Successfully opened notepad and counted 4 images.")));
        assertTrue(result.successful());
        assertTrue(result.observation().contains("Goal successfully completed!"));
    }

    @Test
    void execute_missingSummary_returnsFailure() {
        ToolResultDTO result = tool.execute(req(Map.of()));
        assertFalse(result.successful());
        assertTrue(result.observation().contains("Missing required argument"));
    }

    @Test
    void execute_falseCompletionSummary_returnsFailure() {
        ToolResultDTO result1 = tool.execute(req(Map.of("summary", "No task completed yet.")));
        assertFalse(result1.successful());
        assertTrue(result1.observation().contains("Task completion rejected"));

        ToolResultDTO result2 = tool.execute(req(Map.of("summary", "Task not completed yet due to error")));
        assertFalse(result2.successful());
        assertTrue(result2.observation().contains("Task completion rejected"));

        ToolResultDTO result3 = tool.execute(req(Map.of("summary", "Nothing completed")));
        assertFalse(result3.successful());
        assertTrue(result3.observation().contains("Task completion rejected"));
    }
}
