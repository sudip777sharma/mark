package dev.mark.trajectory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mark.trajectory.entity.ExecutionTrajectoryEntity;
import dev.mark.trajectory.repository.ExecutionTrajectoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class TrajectoryRecorderService {
    private static final Logger log = LoggerFactory.getLogger(TrajectoryRecorderService.class);
    private final ExecutionTrajectoryRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TrajectoryRecorderService(ExecutionTrajectoryRepository repository) {
        this.repository = repository;
    }

    public ExecutionTrajectoryEntity recordTrajectory(
            String taskId,
            String goal,
            String intent,
            List<String> toolSequence,
            Map<String, Object> slots,
            boolean verified,
            long durationMs,
            int llmCount) {
        try {
            if (intent == null || intent.isBlank()) {
                intent = inferIntent(goal);
            }
            String toolSequenceJson = objectMapper.writeValueAsString(toolSequence != null ? toolSequence : List.of());
            String slotsJson = objectMapper.writeValueAsString(slots != null ? slots : Map.of());

            ExecutionTrajectoryEntity entity = new ExecutionTrajectoryEntity(
                    UUID.randomUUID(),
                    taskId,
                    goal,
                    intent,
                    slotsJson,
                    toolSequenceJson,
                    verified,
                    durationMs,
                    llmCount,
                    Instant.now()
            );

            ExecutionTrajectoryEntity saved = repository.save(entity);
            log.info("=== [TRAJECTORY:RECORDED] taskId={} intent={} verified={} duration={}ms",
                    taskId, intent, verified, durationMs);
            return saved;
        } catch (Exception e) {
            log.warn("Failed to record trajectory for taskId={}: {}", taskId, e.getMessage());
            return null;
        }
    }

    public String inferIntent(String goal) {
        String goalLower = goal != null ? goal.toLowerCase() : "";
        if (goalLower.contains("whatsapp")) {
            if (goalLower.contains("send") || goalLower.contains("text") || goalLower.contains("message")) {
                return "whatsapp.send_message";
            }
            if (goalLower.contains("read") || goalLower.contains("check")) {
                return "whatsapp.read_messages";
            }
            return "whatsapp.general";
        }
        if (goalLower.contains("file") || goalLower.contains("create") || goalLower.contains("write")) {
            return "filesystem.file_operation";
        }
        if (goalLower.contains("click") || goalLower.contains("type") || goalLower.contains("desktop")) {
            return "desktop.action";
        }
        return "general.task";
    }
}
