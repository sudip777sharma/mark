package dev.mark.task.service;

import dev.mark.task.dto.TaskResponseDTO;
import dev.mark.task.entity.TaskEntity;
import dev.mark.agent.model.AgentStepModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class TaskSseService {
    private static final Logger log = LoggerFactory.getLogger(TaskSseService.class);
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // Infinite timeout
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));

        return emitter;
    }

    public void broadcastTaskUpdate(TaskEntity entity) {
        if (emitters.isEmpty()) return;

        List<AgentStepModel> steps = entity.getSteps().stream()
                .map(s -> new AgentStepModel(s.getStepNumber(), s.getDescription(), s.getToolName(), s.getToolArguments(), s.getOutcome(), s.getProvider(), s.getTimestamp()))
                .toList();

        TaskResponseDTO response = new TaskResponseDTO(
                entity.getId(),
                entity.getGoal(),
                entity.getStatus(),
                entity.getFinalAnswer(),
                entity.getCurrentAction(),
                entity.getPlan(),
                entity.getLlmRequestCount(),
                steps
        );

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("task_update")
                        .data(response));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    public void broadcastThoughtChunk(String chunk) {
        if (emitters.isEmpty()) return;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("thought_chunk")
                        .data(chunk));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}

