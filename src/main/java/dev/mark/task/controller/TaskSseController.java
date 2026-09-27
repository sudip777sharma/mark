package dev.mark.task.controller;

import dev.mark.task.service.TaskSseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/tasks")
public class TaskSseController {
    private final TaskSseService taskSseService;

    public TaskSseController(TaskSseService taskSseService) {
        this.taskSseService = taskSseService;
    }

    @GetMapping("/stream")
    public SseEmitter streamTaskUpdates() {
        return taskSseService.createEmitter();
    }
}
