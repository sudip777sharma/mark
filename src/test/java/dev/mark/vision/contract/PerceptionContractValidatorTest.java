package dev.mark.vision.contract;

import dev.mark.vision.model.BoundingBox;
import dev.mark.vision.model.ElementSource;
import dev.mark.vision.model.ScreenDimension;
import dev.mark.vision.model.ScreenPoint;
import dev.mark.vision.model.UiElementObservation;
import dev.mark.vision.model.UiObservation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PerceptionContractValidatorTest {

    @Test
    void testValidObservation() {
        ScreenDimension screen = new ScreenDimension(1536, 864, 1.0);
        UiElementObservation el = UiElementObservation.builder("btn-1", ElementSource.UIA, new BoundingBox(100, 100, 120, 40))
                .type("button")
                .text("Submit")
                .confidence(0.99)
                .automationId("submitBtn")
                .build();

        UiObservation obs = new UiObservation("test-id", screen, List.of(el), List.of(), null);
        PerceptionContractValidator.ValidationResult result = PerceptionContractValidator.validate(obs);

        assertTrue(result.valid());
        assertTrue(result.violations().isEmpty());
    }

    @Test
    void testDuplicateElementIdRejected() {
        ScreenDimension screen = new ScreenDimension(1536, 864, 1.0);
        UiElementObservation el1 = UiElementObservation.builder("btn-1", ElementSource.UIA, new BoundingBox(10, 10, 50, 20)).build();
        UiElementObservation el2 = UiElementObservation.builder("btn-1", ElementSource.OCR, new BoundingBox(60, 10, 50, 20)).build();

        UiObservation obs = new UiObservation("test-id", screen, List.of(el1, el2), List.of(), null);
        PerceptionContractValidator.ValidationResult result = PerceptionContractValidator.validate(obs);

        assertFalse(result.valid());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("Duplicate element_id: 'btn-1'")));
    }

    @Test
    void testOutOfBoundsBoxRejected() {
        ScreenDimension screen = new ScreenDimension(1000, 500, 1.0);
        // Box extends to x=1050 (exceeds 1000)
        UiElementObservation el = UiElementObservation.builder("btn-bad", ElementSource.VLM, new BoundingBox(900, 100, 150, 40)).build();

        UiObservation obs = new UiObservation("test-id", screen, List.of(el), List.of(), null);
        PerceptionContractValidator.ValidationResult result = PerceptionContractValidator.validate(obs);

        assertFalse(result.valid());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("exceeds screen width")));
    }

    @Test
    void testCenterOutsideBoxRejected() {
        ScreenDimension screen = new ScreenDimension(1000, 500, 1.0);
        BoundingBox box = new BoundingBox(100, 100, 50, 50);
        ScreenPoint foreignCenter = new ScreenPoint(500, 500);

        UiElementObservation el = UiElementObservation.builder("btn-center-err", ElementSource.VLM, box)
                .center(foreignCenter)
                .build();

        UiObservation obs = new UiObservation("test-id", screen, List.of(el), List.of(), null);
        PerceptionContractValidator.ValidationResult result = PerceptionContractValidator.validate(obs);

        assertFalse(result.valid());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("not contained in bounding box")));
    }

    @Test
    void testHallucinatedAutomationIdOnVlmSourceRejected() {
        ScreenDimension screen = new ScreenDimension(1536, 864, 1.0);
        // VLM source must NEVER invent an automation_id
        UiElementObservation el = UiElementObservation.builder("btn-hallucinated", ElementSource.VLM, new BoundingBox(50, 50, 100, 40))
                .automationId("inventedAutomationId")
                .build();

        UiObservation obs = new UiObservation("test-id", screen, List.of(el), List.of(), null);
        PerceptionContractValidator.ValidationResult result = PerceptionContractValidator.validate(obs);

        assertFalse(result.valid());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("prohibited automation_id")));
    }
}
