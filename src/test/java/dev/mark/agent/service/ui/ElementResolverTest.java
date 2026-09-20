package dev.mark.agent.service.ui;

import dev.mark.agent.model.ui.UiBoundingRectangle;
import dev.mark.agent.model.ui.UiElementModel;
import dev.mark.agent.model.ui.UiTreeModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ElementResolverTest {

    @Test
    void testResolveFound() {
        UiCacheService cache = new UiCacheService();
        ElementResolver resolver = new ElementResolver(cache);

        UiElementModel target = new UiElementModel("ui-target", "Button", "Save", "Path", "Btn", new UiBoundingRectangle(0,0,10,10), List.of(), true, List.of());
        UiElementModel root = new UiElementModel("ui-root", "Window", "App", "Path", "Win", new UiBoundingRectangle(0,0,100,100), List.of(), true, List.of(target));
        UiTreeModel tree = new UiTreeModel("App", "App", root);

        cache.cacheTree("task-1", tree);

        Optional<UiElementModel> resolved = resolver.resolve("task-1", "ui-target");
        assertTrue(resolved.isPresent());
        assertEquals("Save", resolved.get().name());
    }

    @Test
    void testResolveNotFound() {
        UiCacheService cache = new UiCacheService();
        ElementResolver resolver = new ElementResolver(cache);

        UiElementModel root = new UiElementModel("ui-root", "Window", "App", "Path", "Win", new UiBoundingRectangle(0,0,100,100), List.of(), true, List.of());
        UiTreeModel tree = new UiTreeModel("App", "App", root);

        cache.cacheTree("task-1", tree);

        Optional<UiElementModel> resolved = resolver.resolve("task-1", "ui-missing");
        assertTrue(resolved.isEmpty());
    }
}
