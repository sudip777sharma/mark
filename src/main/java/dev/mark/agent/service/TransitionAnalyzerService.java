package dev.mark.agent.service;

import dev.mark.agent.model.ui.UiElementModel;
import dev.mark.agent.service.ui.ElementResolver;
import dev.mark.tool.impl.desktop.DesktopActionExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TransitionAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(TransitionAnalyzerService.class);

    private final ElementResolver elementResolver;
    private final DesktopActionExecutorService executorService;

    public TransitionAnalyzerService(ElementResolver elementResolver, DesktopActionExecutorService executorService) {
        this.elementResolver = elementResolver;
        this.executorService = executorService;
    }

    /**
     * Checks if a target element is still present on the screen using a fast, scoped validation.
     * @param taskId The task ID for resolving cached element data.
     * @param targetId The ID of the element to verify.
     * @return true if the element is likely still valid, false if a transition invalidated it.
     */
    public boolean isTargetStillValid(String taskId, String targetId) {
        Optional<UiElementModel> opt = elementResolver.resolve(taskId, targetId);
        if (opt.isEmpty()) {
            log.warn("TransitionAnalyzer: Target {} not found in cache.", targetId);
            return false;
        }

        UiElementModel element = opt.get();
        boolean exists = executorService.checkElementExists(element);
        
        if (!exists) {
            log.warn("TransitionAnalyzer: Target {} ({} - {}) is NO LONGER VALID on screen. Transition detected.", targetId, element.role(), element.name());
        } else {
            log.debug("TransitionAnalyzer: Target {} is still valid.", targetId);
        }

        return exists;
    }
}
