package com.example.orderapi.service;

import com.example.orderapi.dto.ChatMessage;
import com.example.orderapi.dto.StructuredIntakeData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IntakeOpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(IntakeOpenAiClient.class);
    private static final String SYSTEM_PROMPT = "You are a product intake assistant. Classify the request as bug or feature. "
            + "Ask only minimal clarifying questions. Stop when enough information is collected. "
            + "Return valid JSON only with keys: reply, intakeComplete, structuredData. "
            + "structuredData keys: type, title, description, stepsToReproduce, expectedBehavior, priority, affectedComponents.";

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String model;

    public IntakeOpenAiClient(ObjectMapper objectMapper,
                              @Value("${app.intake.openai.api-key:}") String apiKey,
                              @Value("${app.intake.model:gpt-4.1-mini}") String model) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Missing OpenAI API key. Set app.intake.openai.api-key or OPENAI_API_KEY.");
        }
        this.objectMapper = objectMapper;
        this.model = model;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(15000);
        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public NormalizedIntakeResult collectIntake(List<ChatMessage> messages) {
        List<Map<String, String>> requestMessages = new ArrayList<>();
        requestMessages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));

        if (messages != null) {
            for (ChatMessage message : messages) {
                if (message == null) {
                    continue;
                }
                String role = normalizeRole(message.getRole());
                if (!StringUtils.hasText(role)) {
                    continue;
                }
                String content = message.getContent() != null ? message.getContent().trim() : "";
                if (!content.isEmpty()) {
                    requestMessages.add(Map.of("role", role, "content", content));
                }
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", requestMessages);
        body.put("temperature", 0.2);
        body.put("response_format", Map.of("type", "json_object"));

        try {
            String raw = restClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseNormalizedResult(raw);
        } catch (RestClientResponseException ex) {
            String responseBody = ex.getResponseBodyAsString();
            log.warn("OpenAI intake request failed with status {}", ex.getStatusCode().value());
            if (ex.getStatusCode().value() == 400 && isJsonModeIncompatibility(responseBody)) {
                return fallbackResult("OpenAI model configuration is incompatible with JSON response mode. Please update app.intake.model.");
            }
            return fallbackResult("Intake service is temporarily unavailable. Please try again shortly.");
        } catch (Exception ex) {
            log.warn("OpenAI intake request failed: {}", ex.getMessage());
            return fallbackResult();
        }
    }

    private NormalizedIntakeResult parseNormalizedResult(String rawResponse) {
        try {
            if (!StringUtils.hasText(rawResponse)) {
                log.warn("OpenAI intake response body was empty");
                return fallbackResult();
            }
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode intakeJson = extractStructuredJson(root);
            if (intakeJson == null || intakeJson.isNull() || intakeJson.isMissingNode()) {
                log.warn("OpenAI intake JSON extraction failed for configured chat response shape");
                return fallbackResult();
            }

            String reply = intakeJson.path("reply").asText("").trim();
            StructuredIntakeData structuredData = toStructuredData(intakeJson.path("structuredData"));
            boolean intakeComplete = intakeJson.path("intakeComplete").asBoolean(false);

            if (reply.isEmpty()) {
                return new NormalizedIntakeResult(
                        "I need a bit more detail to capture this intake. Please share whether this is a bug or feature, plus title and description.",
                        false,
                        structuredData
                );
            }

            return new NormalizedIntakeResult(reply, intakeComplete, structuredData);
        } catch (Exception ex) {
            log.warn("Failed to parse OpenAI intake response JSON", ex);
            return fallbackResult();
        }
    }

    private StructuredIntakeData toStructuredData(JsonNode node) {
        StructuredIntakeData data = new StructuredIntakeData();
        if (node == null || node.isMissingNode() || node.isNull() || !node.isObject()) {
            data.setTitle("");
            data.setDescription("");
            data.setStepsToReproduce("");
            data.setExpectedBehavior("");
            data.setAffectedComponents(Collections.emptyList());
            return data;
        }
        data.setType(validType(node.path("type").asText(null)));
        data.setTitle(blankToEmpty(node.path("title").asText(null)));
        data.setDescription(blankToEmpty(node.path("description").asText(null)));
        data.setStepsToReproduce(blankToEmpty(node.path("stepsToReproduce").asText(null)));
        data.setExpectedBehavior(blankToEmpty(node.path("expectedBehavior").asText(null)));
        data.setPriority(validPriority(node.path("priority").asText(null)));

        List<String> affectedComponents = new ArrayList<>();
        JsonNode affected = node.path("affectedComponents");
        if (affected.isArray()) {
            for (JsonNode item : affected) {
                String value = blankToNull(item.asText(null));
                if (value != null) {
                    affectedComponents.add(value);
                }
            }
        }
        data.setAffectedComponents(affectedComponents);
        return data;
    }

    private String normalizeRole(String role) {
        if ("assistant".equals(role)) {
            return "assistant";
        }
        if ("system".equals(role)) {
            return "system";
        }
        if ("developer".equals(role)) {
            return "developer";
        }
        if ("tool".equals(role)) {
            return "tool";
        }
        if ("user".equals(role)) {
            return "user";
        }
        return null;
    }

    private String unwrapJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > -1 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private JsonNode extractStructuredJson(JsonNode root) {
        JsonNode messageNode = root.path("choices").path(0).path("message");
        JsonNode contentNode = messageNode.path("content");
        return parseJsonFromContentNode(contentNode);
    }

    private JsonNode parseJsonFromContentNode(JsonNode contentNode) {
        if (contentNode.isTextual()) {
            return parseJsonFromContent(contentNode.asText(""));
        }
        if (contentNode.isArray()) {
            List<String> fragments = new ArrayList<>();
            for (JsonNode item : contentNode) {
                if (item.isTextual()) {
                    JsonNode parsed = parseJsonFromContent(item.asText(""));
                    if (parsed != null) {
                        return parsed;
                    }
                    fragments.add(item.asText(""));
                } else {
                    String text = item.path("text").asText("");
                    String value = item.path("value").asText("");
                    JsonNode parsedText = parseJsonFromContent(text);
                    if (parsedText != null) {
                        return parsedText;
                    }
                    JsonNode parsedValue = parseJsonFromContent(value);
                    if (parsedValue != null) {
                        return parsedValue;
                    }
                    if (StringUtils.hasText(text)) {
                        fragments.add(text);
                    }
                    if (StringUtils.hasText(value)) {
                        fragments.add(value);
                    }
                }
            }
            return parseJsonFromContent(String.join("\n", fragments));
        }
        if (contentNode.isObject()) {
            String text = contentNode.path("text").asText("");
            if (StringUtils.hasText(text)) {
                return parseJsonFromContent(text);
            }
        }
        return null;
    }

    private JsonNode parseJsonFromContent(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String jsonOnly = unwrapJson(content);
        try {
            return objectMapper.readTree(jsonOnly);
        } catch (Exception ex) {
            return null;
        }
    }

    private String blankToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String blankToEmpty(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    private boolean isJsonModeIncompatibility(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode error = root.path("error");
            String param = error.path("param").asText("").toLowerCase();
            String code = error.path("code").asText("").toLowerCase();
            String type = error.path("type").asText("").toLowerCase();
            String message = error.path("message").asText("").toLowerCase();
            return param.contains("response_format")
                    || code.contains("response_format")
                    || type.contains("invalid_request")
                    || message.contains("response_format")
                    || message.contains("json_object");
        } catch (Exception ignored) {
            return false;
        }
    }

    private String validType(String value) {
        String normalized = blankToNull(value);
        if ("bug".equals(normalized) || "feature".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private String validPriority(String value) {
        String normalized = blankToNull(value);
        if ("low".equals(normalized) || "medium".equals(normalized) || "high".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private NormalizedIntakeResult fallbackResult() {
        return fallbackResult("I need a bit more detail to capture this intake. Please share whether this is a bug or feature, plus title and description.");
    }

    private NormalizedIntakeResult fallbackResult(String reply) {
        StructuredIntakeData fallbackData = new StructuredIntakeData();
        fallbackData.setAffectedComponents(Collections.emptyList());
        return new NormalizedIntakeResult(
                reply,
                false,
                fallbackData
        );
    }

    public static class NormalizedIntakeResult {
        private final String reply;
        private final boolean intakeComplete;
        private final StructuredIntakeData structuredData;

        public NormalizedIntakeResult(String reply, boolean intakeComplete, StructuredIntakeData structuredData) {
            this.reply = reply;
            this.intakeComplete = intakeComplete;
            this.structuredData = structuredData;
        }

        public String getReply() {
            return reply;
        }

        public boolean isIntakeComplete() {
            return intakeComplete;
        }

        public StructuredIntakeData getStructuredData() {
            return structuredData;
        }
    }
}
