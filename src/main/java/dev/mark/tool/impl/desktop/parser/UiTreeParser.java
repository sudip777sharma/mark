package dev.mark.tool.impl.desktop.parser;

import dev.mark.agent.model.ui.UiBoundingRectangle;
import dev.mark.agent.model.ui.UiElementModel;
import dev.mark.agent.model.ui.UiTreeModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UiTreeParser {

    // Regex to match: "  ControlType | Name='...' | Class='...' | Rect=[x,y,w,h]"
    private static final Pattern LINE_PATTERN = Pattern.compile("^(\\s*)(.*?) \\| Name='(.*?)' \\| Class='(.*?)' \\| Rect=\\[(.*?)\\]$");

    public static UiTreeModel parse(String rawOutput, String applicationName, String windowTitle) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return new UiTreeModel(applicationName, windowTitle, null);
        }

        String[] lines = rawOutput.split("\n");
        UiElementModel root = null;
        Stack<ElementWithDepth> stack = new Stack<>();

        for (String line : lines) {
            if (line.isBlank() || line.startsWith("Title:") || line.startsWith("Process:") || line.startsWith("PID:")) {
                continue; // Skip header or empty lines
            }
            if (line.contains("... (output truncated")) {
                continue; // Skip truncation warning
            }

            Matcher matcher = LINE_PATTERN.matcher(line);
            if (matcher.matches()) {
                String indent = matcher.group(1);
                int depth = indent.length() / 2; // 2 spaces per depth
                String role = matcher.group(2).trim();
                String name = matcher.group(3);
                String className = matcher.group(4);
                String rectStr = matcher.group(5);

                UiBoundingRectangle bounds = parseRect(rectStr);

                // We initially leave id, path, actions, and isActionable blank/default.
                // The DeterministicIdentityGenerator and SemanticUiFilter will populate them.
                UiElementModel element = new UiElementModel(
                        null, role, name, className, null, bounds, new ArrayList<>(), false, new ArrayList<>()
                );

                ElementWithDepth current = new ElementWithDepth(element, depth);

                while (!stack.isEmpty() && stack.peek().depth >= depth) {
                    stack.pop();
                }

                if (stack.isEmpty()) {
                    root = element;
                } else {
                    stack.peek().element.children().add(element);
                }

                stack.push(current);
            }
        }

        return new UiTreeModel(applicationName, windowTitle, root);
    }

    private static UiBoundingRectangle parseRect(String rectStr) {
        try {
            String[] parts = rectStr.split(",");
            if (parts.length == 4) {
                return new UiBoundingRectangle(
                        Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim()),
                        Integer.parseInt(parts[3].trim())
                );
            }
        } catch (NumberFormatException ignored) {
        }
        return new UiBoundingRectangle(0, 0, 0, 0);
    }

    private static class ElementWithDepth {
        final UiElementModel element;
        final int depth;

        ElementWithDepth(UiElementModel element, int depth) {
            this.element = element;
            this.depth = depth;
        }
    }
}
