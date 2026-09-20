package dev.mark.agent.service.ui;

import dev.mark.agent.model.ui.UiElementModel;
import dev.mark.agent.model.ui.UiTreeModel;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ElementResolver {

    private final UiCacheService cacheService;

    public ElementResolver(UiCacheService cacheService) {
        this.cacheService = cacheService;
    }

    public Optional<UiElementModel> resolve(String taskId, String elementId) {
        UiTreeModel latestTree = cacheService.getLatestTree(taskId);
        if (latestTree == null || latestTree.root() == null) {
            return Optional.empty();
        }

        return findElement(latestTree.root(), elementId);
    }

    private Optional<UiElementModel> findElement(UiElementModel current, String targetId) {
        if (targetId.equals(current.id())) {
            return Optional.of(current);
        }

        if (current.children() != null) {
            for (UiElementModel child : current.children()) {
                Optional<UiElementModel> found = findElement(child, targetId);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }
}
