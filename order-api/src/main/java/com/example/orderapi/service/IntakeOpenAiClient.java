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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

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
        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
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
        } catch (Exception ex) {
            log.warn("OpenAI intake request failed", ex);
            return fallbackResult();
        }
    }

    private NormalizedIntakeResult parseNormalizedResult(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse != null ? rawResponse : "{}");
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (content.isEmpty()) {
                return fallbackResult();
            }

            String jsonOnly = unwrapJson(content);
            JsonNode intakeJson = objectMapper.readTree(jsonOnly);

            String reply = intakeJson.path("reply").asText("").trim();
            boolean intakeComplete = intakeJson.path("intakeComplete").asBoolean(false);
            StructuredIntakeData structuredData = toStructuredData(intakeJson.path("structuredData"));

            if (reply.isEmpty()) {
                return fallbackResult();
            }

            return new NormalizedIntakeResult(reply, intakeComplete, structuredData);
        } catch (Exception ex) {
            log.warn("Failed to parse OpenAI intake response JSON", ex);
            return fallbackResult();
        }
    }

    private StructuredIntakeData toStructuredData(JsonNode node) {
        StructuredIntakeData data = new StructuredIntakeData();
        data.setType(validType(node.path("type").asText(null)));
        data.setTitle(blankToNull(node.path("title").asText(null)));
        data.setDescription(blankToNull(node.path("description").asText(null)));
        data.setStepsToReproduce(blankToNull(node.path("stepsToReproduce").asText(null)));
        data.setExpectedBehavior(blankToNull(node.path("expectedBehavior").asText(null)));
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
        return "user";
    }

    private String unwrapJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > -1 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private String blankToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
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
        StructuredIntakeData fallbackData = new StructuredIntakeData();
        fallbackData.setAffectedComponents(Collections.emptyList());
        return new NormalizedIntakeResult(
                "I need a bit more detail to capture this intake. Please share whether this is a bug or feature, plus title and description.",
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
