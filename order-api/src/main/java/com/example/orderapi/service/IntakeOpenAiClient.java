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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class IntakeOpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(IntakeOpenAiClient.class);
    private static final Pattern FENCED_JSON_PATTERN =
            Pattern.compile("^```(?:json)?\\s*(.*?)\\s*```$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FENCED_JSON_ANYWHERE_PATTERN =
            Pattern.compile("```(?:json)?\\s*(\\{.*?})\\s*```", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
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
            log.warn("OpenAI intake request failed with status {} body={}", ex.getStatusCode(), safeSnippet(responseBody));
            if (ex.getStatusCode().value() == 400 && responseBody != null && responseBody.contains("response_format")) {
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
        if ("tool".equals(role)) {
            return "tool";
        }
        if ("user".equals(role)) {
            return "user";
        }
        return "user";
    }

    private String unwrapJson(String content) {
        String trimmed = content.trim();
        Matcher matcher = FENCED_JSON_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            return matcher.group(1).trim();
        }
        Matcher inlineFenceMatcher = FENCED_JSON_ANYWHERE_PATTERN.matcher(trimmed);
        if (inlineFenceMatcher.find()) {
            return inlineFenceMatcher.group(1).trim();
        }
        return trimmed;
    }

    private JsonNode extractStructuredJson(JsonNode root) {
        JsonNode messageNode = root.path("choices").path(0).path("message");
        JsonNode contentNode = messageNode.path("content");
        JsonNode fromContent = parseJsonFromContentNode(contentNode);
        if (fromContent != null) {
            return fromContent;
        }

        JsonNode fromOutputText = parseJsonFromContent(root.path("output_text").asText(""));
        if (fromOutputText != null) {
            return fromOutputText;
        }

        JsonNode altText = root.path("output").path(0).path("content").path(0).path("text");
        return parseJsonFromContent(altText.asText(""));
    }

    private JsonNode parseJsonFromContentNode(JsonNode contentNode) {
        if (contentNode.isTextual()) {
            return parseJsonFromContent(contentNode.asText(""));
        }
        if (contentNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : contentNode) {
                if (item.isTextual()) {
                    sb.append(item.asText(""));
                } else {
                    sb.append(item.path("text").asText(""));
                    sb.append(item.path("value").asText(""));
                }
            }
            return parseJsonFromContent(sb.toString());
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
            String extracted = extractFirstJsonObject(jsonOnly);
            if (extracted == null) {
                return null;
            }
            try {
                return objectMapper.readTree(extracted);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private String extractFirstJsonObject(String value) {
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') {
                if (start < 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                if (depth > 0) {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        return value.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }

    private String safeSnippet(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > 240 ? normalized.substring(0, 240) + "..." : normalized;
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
        return fallbackResult("I need a bit more detail to capture this intake. Please share whether this is a bug or feature, plus title and description.");
    }

    private NormalizedIntakeResult fallbackResult(String reply) {
        StructuredIntakeData fallbackData = new StructuredIntakeData();
        fallbackData.setTitle("");
        fallbackData.setDescription("");
        fallbackData.setStepsToReproduce("");
        fallbackData.setExpectedBehavior("");
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
