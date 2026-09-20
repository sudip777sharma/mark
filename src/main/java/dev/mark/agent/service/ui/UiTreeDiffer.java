package dev.mark.agent.service.ui;

import dev.mark.agent.model.ui.UiDiffResult;
import dev.mark.agent.model.ui.UiElementModel;
import dev.mark.agent.model.ui.UiTreeModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UiTreeDiffer {

    public static UiDiffResult diff(UiTreeModel previous, UiTreeModel current) {
        if (previous == null || previous.root() == null) {
            return new UiDiffResult(false, List.of("ALL"), List.of(), List.of());
        }
        if (current == null || current.root() == null) {
            return new UiDiffResult(false, List.of(), List.of("ALL"), List.of());
        }

        Map<String, UiElementModel> prevMap = flatten(previous.root());
        Map<String, UiElementModel> currMap = flatten(current.root());

        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> changed = new ArrayList<>();

        for (String currId : currMap.keySet()) {
            if (!prevMap.containsKey(currId)) {
                added.add(currId);
            } else {
                UiElementModel prevEl = prevMap.get(currId);
                UiElementModel currEl = currMap.get(currId);
                if (hasPropertiesChanged(prevEl, currEl)) {
                    changed.add(currId);
                }
            }
        }

        for (String prevId : prevMap.keySet()) {
            if (!currMap.containsKey(prevId)) {
                removed.add(prevId);
            }
        }

        boolean isStable = added.isEmpty() && removed.isEmpty() && changed.isEmpty();
        return new UiDiffResult(isStable, added, removed, changed);
    }

    private static Map<String, UiElementModel> flatten(UiElementModel root) {
        Map<String, UiElementModel> map = new HashMap<>();
        if (root.id() != null) {
            map.put(root.id(), root);
        }
        if (root.children() != null) {
            for (UiElementModel child : root.children()) {
                map.putAll(flatten(child));
            }
        }
        return map;
    }

    private static boolean hasPropertiesChanged(UiElementModel prev, UiElementModel curr) {
        if (!java.util.Objects.equals(prev.name(), curr.name())) return true;
        if (!java.util.Objects.equals(prev.role(), curr.role())) return true;
        if (!java.util.Objects.equals(prev.bounds(), curr.bounds())) return true;
        if (prev.isActionable() != curr.isActionable()) return true;
        return false;
    }
}
