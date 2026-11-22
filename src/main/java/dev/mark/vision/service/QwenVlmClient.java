package dev.mark.vision.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mark.vision.config.VisionPropertiesConfig;
import dev.mark.vision.model.BoundingBox;
import dev.mark.vision.model.CoordinateTransformer;
import dev.mark.vision.model.ElementSource;
import dev.mark.vision.model.ScreenDimension;
import dev.mark.vision.model.UiElementObservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client for Tier-2 Qwen3-VL 2B (Local Deep VLM): performs deep visual reasoning and spatial grounding.
 */
@Service
public class QwenVlmClient {

    private static final Logger log = LoggerFactory.getLogger(QwenVlmClient.class);
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("(?s)\\{.*\\}");

    private final RestClient restClient;
    private final VisionPropertiesConfig config;
    private final ObjectMapper objectMapper;

    @Autowired
    public QwenVlmClient(VisionPropertiesConfig config, ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder
                .baseUrl(config.getQwen().getBaseUrl())
                .build();
    }

    public List<UiElementObservation> inspectScreen(String base64Image, ScreenDimension screen, String targetQuery) {
        log.info("Querying Tier-2 Qwen3-VL 2B for screen {}x{}, target: '{}'", screen.width(), screen.height(), targetQuery);

        String prompt = String.format("""
                You are MARK Vision Engine. Analyze the screenshot to locate UI elements matching the query: "%s".
                Return ONLY a valid JSON object matching this schema:
                {
                  "elements": [
                    {
                      "name": "<label or description>",
                      "type": "button | input | icon | tab | text | link",
                      "bbox_2d": [ymin, xmin, ymax, xmax],
                      "confidence": 0.95
                    }
                  ]
                }
                Coordinates MUST be normalized relative integers in range [0, 1000]. Do not write markdown commentary outside JSON.
                """, targetQuery != null ? targetQuery : "all key clickable controls");

        Map<String, Object> payload = Map.of(
                "model", config.getQwen().getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", "You are a desktop UI grounding assistant. Respond only with valid JSON."),
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "text", "text", prompt),
                                Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64," + base64Image))
                        ))
                ),
                "temperature", 0.1,
                "max_tokens", 768
        );

        CoordinateTransformer transformer = new CoordinateTransformer(screen);
        List<UiElementObservation> elements = new ArrayList<>();

        try {
            String rawResponse = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            if (rawResponse != null && !rawResponse.isBlank()) {
                elements.addAll(parseQwenResponse(rawResponse, transformer, screen));
            }
        } catch (Exception e) {
            log.error("Tier-2 Qwen3-VL inference failed: {}", e.getMessage(), e);
        }

        return elements;
    }

    public List<UiElementObservation> parseQwenResponse(String rawResponse, CoordinateTransformer transformer, ScreenDimension screen) {
        List<UiElementObservation> elements = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String content = null;

            if (root.has("choices") && root.get("choices").isArray() && !root.get("choices").isEmpty()) {
                JsonNode message = root.get("choices").get(0).get("message");
                if (message != null && message.has("content")) {
                    content = message.get("content").asText();
                }
            } else if (root.has("response")) {
                content = root.get("response").asText();
            }

            if (content == null || content.isBlank()) {
                return elements;
            }

            // Extract JSON object substring if wrapped in markdown
            Matcher matcher = JSON_BLOCK_PATTERN.matcher(content);
            String jsonToParse = matcher.find() ? matcher.group() : content;

            JsonNode parsed = objectMapper.readTree(jsonToParse);
            JsonNode items = parsed.has("elements") ? parsed.get("elements") : null;

            if (items != null && items.isArray()) {
                int index = 1;
                for (JsonNode item : items) {
                    String name = item.has("name") ? item.get("name").asText() : "element";
                    String type = item.has("type") ? item.get("type").asText() : "control";
                    double confidence = item.has("confidence") ? item.get("confidence").asDouble() : 0.85;

                    if (item.has("bbox_2d") && item.get("bbox_2d").isArray() && item.get("bbox_2d").size() >= 4) {
                        JsonNode b = item.get("bbox_2d");
                        int ymin = b.get(0).asInt();
                        int xmin = b.get(1).asInt();
                        int ymax = b.get(2).asInt();
                        int xmax = b.get(3).asInt();

                        BoundingBox box = transformer.denormalizeBox(ymin, xmin, ymax, xmax);
                        String id = "qwen-" + (index++);

                        elements.add(UiElementObservation.builder(id, ElementSource.VLM, box)
                                .text(name)
                                .type(type)
                                .confidence(confidence)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Qwen3-VL response: {}", e.getMessage());
        }
        return elements;
    }
}
