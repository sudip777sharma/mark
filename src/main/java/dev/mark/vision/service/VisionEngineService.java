package dev.mark.vision.service;

import dev.mark.vision.contract.PerceptionContractValidator;
import dev.mark.vision.model.BoundingBox;
import dev.mark.vision.model.ElementSource;
import dev.mark.vision.model.ScreenDimension;
import dev.mark.vision.model.UiElementObservation;
import dev.mark.vision.model.UiObservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Dual-tier local vision perception engine:
 * Tier 1: Mirage VLM (22 MB) for sub-second real-time element detection & OCR.
 * Tier 2: Qwen3-VL 2B for deep visual reasoning and layout disambiguation.
 */
@Service
public class VisionEngineService {

    private static final Logger log = LoggerFactory.getLogger(VisionEngineService.class);
    private static final int MAX_DOWNSIZED_DIMENSION = 1024;

    private final MirageVlmClient mirageClient;
    private final QwenVlmClient qwenClient;

    @Autowired
    public VisionEngineService(MirageVlmClient mirageClient, QwenVlmClient qwenClient) {
        this.mirageClient = mirageClient;
        this.qwenClient = qwenClient;
    }

    public UiObservation perceive(BufferedImage image, ScreenDimension screen, String targetDescription) {
        String screenshotId = UUID.randomUUID().toString();
        List<String> uncertainties = new ArrayList<>();
        List<UiElementObservation> detectedElements = new ArrayList<>();

        if (image == null || screen == null) {
            uncertainties.add("Image or ScreenDimension is null");
            return new UiObservation(screenshotId, screen != null ? screen : new ScreenDimension(1536, 864, 1.0),
                    List.of(), uncertainties, Instant.now().toString());
        }

        String base64Payload = encodeImageOptimized(image);

        // 1. Tier 1: Fast Mirage VLM detection (~100 ms)
        long startT1 = System.currentTimeMillis();
        List<UiElementObservation> tier1Elements = mirageClient.detectElements(base64Payload, screen, targetDescription);
        long durT1 = System.currentTimeMillis() - startT1;
        log.info("Tier-1 Mirage VLM completed in {} ms, detected {} elements", durT1, tier1Elements.size());

        boolean confidentMatchFound = false;
        if (!tier1Elements.isEmpty()) {
            if (targetDescription == null || targetDescription.isBlank()) {
                confidentMatchFound = true;
                detectedElements.addAll(tier1Elements);
            } else {
                String queryLower = targetDescription.toLowerCase();
                for (UiElementObservation el : tier1Elements) {
                    if (el.text() != null && el.text().toLowerCase().contains(queryLower) && el.confidence() >= 0.80) {
                        confidentMatchFound = true;
                        detectedElements.add(el);
                    }
                }
            }
        }

        // 2. Tier 2: Escalate to Qwen3-VL 2B if Tier 1 lacked confident match
        if (!confidentMatchFound) {
            log.info("Tier-1 lacked confident match for '{}'. Escalating to Tier-2 Qwen3-VL 2B...", targetDescription);
            uncertainties.add("Tier-1 Mirage unconfident; escalated to Tier-2 Qwen3-VL");

            long startT2 = System.currentTimeMillis();
            List<UiElementObservation> tier2Elements = qwenClient.inspectScreen(base64Payload, screen, targetDescription);
            long durT2 = System.currentTimeMillis() - startT2;
            log.info("Tier-2 Qwen3-VL completed in {} ms, detected {} elements", durT2, tier2Elements.size());

            if (!tier2Elements.isEmpty()) {
                detectedElements.addAll(tier2Elements);
            } else if (!tier1Elements.isEmpty()) {
                // Keep Tier 1 items as fallback if Tier 2 returned empty
                detectedElements.addAll(tier1Elements);
            }
        }

        UiObservation observation = new UiObservation(
                screenshotId,
                screen,
                detectedElements,
                uncertainties,
                Instant.now().toString()
        );

        // 3. Strict Contract Validation
        PerceptionContractValidator.ValidationResult valResult = PerceptionContractValidator.validate(observation);
        if (!valResult.valid()) {
            log.warn("Observation failed validation invariants: {}", valResult.violations());
            uncertainties.addAll(valResult.violations());
            return new UiObservation(screenshotId, screen, filterValidElements(detectedElements, screen), uncertainties, Instant.now().toString());
        }

        return observation;
    }

    private List<UiElementObservation> filterValidElements(List<UiElementObservation> elements, ScreenDimension screen) {
        return elements.stream()
                .filter(el -> el != null && el.boundingBox() != null)
                .filter(el -> el.boundingBox().x() >= 0 && el.boundingBox().y() >= 0)
                .filter(el -> el.boundingBox().x() + el.boundingBox().width() <= screen.width())
                .filter(el -> el.boundingBox().y() + el.boundingBox().height() <= screen.height())
                .toList();
    }

    private String encodeImageOptimized(BufferedImage original) {
        BufferedImage toEncode = original;

        // Downscale large screenshots to max dimension to optimize CPU patch token latency
        if (original.getWidth() > MAX_DOWNSIZED_DIMENSION || original.getHeight() > MAX_DOWNSIZED_DIMENSION) {
            double scale = Math.min(
                    (double) MAX_DOWNSIZED_DIMENSION / original.getWidth(),
                    (double) MAX_DOWNSIZED_DIMENSION / original.getHeight()
            );
            int newW = (int) Math.round(original.getWidth() * scale);
            int newH = (int) Math.round(original.getHeight() * scale);

            BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.drawImage(original.getScaledInstance(newW, newH, Image.SCALE_SMOOTH), 0, 0, null);
            g.dispose();
            toEncode = scaled;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(toEncode, "PNG", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            log.error("Failed to encode image to base64: {}", e.getMessage());
            return "";
        }
    }
}
