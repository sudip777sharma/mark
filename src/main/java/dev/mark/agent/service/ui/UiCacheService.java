package dev.mark.agent.service.ui;

import dev.mark.agent.model.ui.UiTreeModel;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UiCacheService {

    private final Map<String, UiTreeModel> latestCache = new ConcurrentHashMap<>();
    private final Map<String, UiTreeModel> previousCache = new ConcurrentHashMap<>();

    public void cacheTree(String taskId, UiTreeModel tree) {
        if (taskId != null && tree != null) {
            UiTreeModel current = latestCache.get(taskId);
            if (current != null) {
                previousCache.put(taskId, current);
            }
            latestCache.put(taskId, tree);
        }
    }

    public UiTreeModel getLatestTree(String taskId) {
        if (taskId == null) return null;
        return latestCache.get(taskId);
    }
    
    public UiTreeModel getPreviousTree(String taskId) {
        if (taskId == null) return null;
        return previousCache.get(taskId);
    }
    
    public void clear(String taskId) {
        if (taskId != null) {
            latestCache.remove(taskId);
            previousCache.remove(taskId);
        }
    }
}
