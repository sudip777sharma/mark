package dev.mark.vision.contract;

import dev.mark.vision.model.BoundingBox;
import dev.mark.vision.model.ElementSource;
import dev.mark.vision.model.ScreenDimension;
import dev.mark.vision.model.UiElementObservation;
import dev.mark.vision.model.UiObservation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Enforces strict validation invariants on UI observations before they reach action executors.
 */
public class PerceptionContractValidator {

    public record ValidationResult(boolean valid, List<String> violations) {
        public static ValidationResult success() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult failure(List<String> violations) {
            return new ValidationResult(false, List.copyOf(violations));
        }
    }

    public static ValidationResult validate(UiObservation observation) {
        if (observation == null) {
            return ValidationResult.failure(List.of("Observation cannot be null"));
        }

        List<String> violations = new ArrayList<>();
        ScreenDimension screen = observation.screen();
        if (screen == null) {
            violations.add("Observation screen dimension cannot be null");
            return ValidationResult.failure(violations);
        }

        if (screen.width() <= 0 || screen.height() <= 0) {
            violations.add(String.format("Invalid screen bounds: %dx%d", screen.width(), screen.height()));
        }

        Set<String> elementIds = new HashSet<>();
        List<UiElementObservation> elements = observation.elements();

        if (elements != null) {
            for (int i = 0; i < elements.size(); i++) {
                UiElementObservation el = elements.get(i);
                if (el == null) {
                    violations.add(String.format("Element at index %d is null", i));
                    continue;
                }

                // Unique element_id
                if (!elementIds.add(el.elementId())) {
                    violations.add(String.format("Duplicate element_id: '%s'", el.elementId()));
                }

                // Confidence bounds
                if (el.confidence() < 0.0 || el.confidence() > 1.0) {
                    violations.add(String.format("Element '%s' confidence out of range [0, 1]: %.2f", el.elementId(), el.confidence()));
                }

                // Bounding box within screen boundaries
                BoundingBox box = el.boundingBox();
                if (box == null) {
                    violations.add(String.format("Element '%s' bounding box is null", el.elementId()));
                } else {
                    if (box.x() < 0 || box.y() < 0) {
                        violations.add(String.format("Element '%s' origin negative: (%d, %d)", el.elementId(), box.x(), box.y()));
                    }
                    if (box.x() + box.width() > screen.width()) {
                        violations.add(String.format("Element '%s' exceeds screen width: %d + %d > %d",
                                el.elementId(), box.x(), box.width(), screen.width()));
                    }
                    if (box.y() + box.height() > screen.height()) {
                        violations.add(String.format("Element '%s' exceeds screen height: %d + %d > %d",
                                el.elementId(), box.y(), box.height(), screen.height()));
                    }

                    // Center within box
                    if (el.center() != null) {
                        if (!box.contains(el.center())) {
                            violations.add(String.format("Element '%s' center (%d, %d) not contained in bounding box %s",
                                    el.elementId(), el.center().x(), el.center().y(), box));
                        }
                    }
                }

                // Hallucinated Automation ID protection: non-UIA elements must not invent automation_id
                if (el.automationId() != null && !el.automationId().isBlank() && el.source() != ElementSource.UIA && el.source() != ElementSource.FUSED) {
                    violations.add(String.format("Element '%s' from source '%s' has prohibited automation_id '%s' (only UIA source permitted)",
                            el.elementId(), el.source(), el.automationId()));
                }
            }
        }

        return violations.isEmpty() ? ValidationResult.success() : ValidationResult.failure(violations);
    }
}
