package dev.mark.vision.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mark.vision.config.VisionPropertiesConfig;
import dev.mark.vision.model.BoundingBox;
import dev.mark.vision.model.CoordinateTransformer;
import dev.mark.vision.model.ElementSource;
import dev.mark.vision.model.ScreenDimension;
import dev.mark.vision.model.ScreenPoint;
import dev.mark.vision.model.UiElementObservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Client for Tier-1 Mirage VLM (22 MB): ultra-fast edge model for sub-second UI element detection and OCR.
 */
@Service
public class MirageVlmClient {

    private static final Logger log = LoggerFactory.getLogger(MirageVlmClient.class);
    private final RestClient restClient;
    private final VisionPropertiesConfig config;
    private final ObjectMapper objectMapper;

    @Autowired
    public MirageVlmClient(VisionPropertiesConfig config, ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder
                .baseUrl(config.getMirage().getBaseUrl())
                .build();
    }

    public List<UiElementObservation> detectElements(String base64Image, ScreenDimension screen, String targetQuery) {
        log.info("Querying Tier-1 Mirage VLM (22 MB) for screen {}x{}", screen.width(), screen.height());

        Map<String, Object> payload = Map.of(
                "model", config.getMirage().getModel(),
                "image", base64Image,
                "task", "detect_and_ocr",
                "query", targetQuery != null ? targetQuery : "",
                "options", Map.of("temperature", 0.0, "max_tokens", 512)
        );

        CoordinateTransformer transformer = new CoordinateTransformer(screen);
        List<UiElementObservation> elements = new ArrayList<>();

        try {
            String rawJson = restClient.post()
                    .uri("/describe")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            if (rawJson != null && !rawJson.isBlank()) {
                elements.addAll(parseMirageResponse(rawJson, transformer, screen));
            }
        } catch (Exception e) {
            log.warn("Mirage VLM endpoint unavailable or timed out ({}). Tier-2 escalation will be utilized.", e.getMessage());
        }

        return elements;
    }

    public List<UiElementObservation> parseMirageResponse(String json, CoordinateTransformer transformer, ScreenDimension screen) {
        List<UiElementObservation> elements = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.has("elements") ? root.get("elements") : root.has("objects") ? root.get("objects") : null;

            if (items != null && items.isArray()) {
                int index = 1;
                for (JsonNode item : items) {
                    String name = item.has("name") ? item.get("name").asText() : item.has("text") ? item.get("text").asText() : "element";
                    String type = item.has("type") ? item.get("type").asText() : "control";
                    double confidence = item.has("confidence") ? item.get("confidence").asDouble() : 0.90;

                    BoundingBox box = null;
                    if (item.has("bbox_2d") && item.get("bbox_2d").isArray() && item.get("bbox_2d").size() >= 4) {
                        JsonNode b = item.get("bbox_2d");
                        int ymin = b.get(0).asInt();
                        int xmin = b.get(1).asInt();
                        int ymax = b.get(2).asInt();
                        int xmax = b.get(3).asInt();
                        box = transformer.denormalizeBox(ymin, xmin, ymax, xmax);
                    } else if (item.has("point") && item.get("point").isArray() && item.get("point").size() >= 2) {
                        int px = item.get("point").get(0).asInt();
                        int py = item.get("point").get(1).asInt();
                        ScreenPoint pt = transformer.denormalizePoint(px, py);
                        box = new BoundingBox(Math.max(0, pt.x() - 20), Math.max(0, pt.y() - 15), 40, 30);
                    }

                    if (box != null) {
                        String id = "mirage-" + (index++);
                        elements.add(UiElementObservation.builder(id, ElementSource.VLM, box)
                                .text(name)
                                .type(type)
                                .confidence(confidence)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Mirage JSON response: {}", e.getMessage());
        }
        return elements;
    }
}
