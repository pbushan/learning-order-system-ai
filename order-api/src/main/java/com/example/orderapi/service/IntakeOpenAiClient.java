package com.example.orderapi.service;

import com.example.orderapi.dto.ChatMessage;
import com.example.orderapi.dto.DecompositionResponse;
import com.example.orderapi.dto.DecompositionStory;
import com.example.orderapi.dto.PrSafety;
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
import java.nio.charset.StandardCharsets;

@Service
public class IntakeOpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(IntakeOpenAiClient.class);
    static final int MAX_TOTAL_MESSAGES = 20;
    static final int MAX_HISTORY_MESSAGES = Math.max(0, MAX_TOTAL_MESSAGES - 1);
    static final int MAX_CONTENT_CHARS = 2000;
    static final int TRUNCATION_BOUNDARY_WINDOW = 80;
    private static final int MAX_DECOMPOSITION_PAYLOAD_BYTES = 12000;
    private static final int MAX_DECOMPOSITION_REQUEST_BYTES = 20000;
    private static final int MAX_DECOMPOSITION_RESPONSE_BYTES = 120000;
    private static final int MAX_DECOMPOSITION_FIELD_CHARS = 2000;
    private static final int MAX_DECOMPOSITION_COMPONENTS = 20;
    private static final int MAX_DECOMPOSITION_FALLBACK_FIELD_CHARS = 500;
    private static final int MAX_DECOMPOSITION_FALLBACK_COMPONENTS = 10;
    private static final String SYSTEM_PROMPT = "You are a product intake assistant. Classify the request as bug or feature. "
            + "Ask only minimal clarifying questions. Stop when enough information is collected. "
            + "Return valid JSON only with keys: reply, intakeComplete, structuredData. "
            + "structuredData keys: type, title, description, stepsToReproduce, expectedBehavior, priority, affectedComponents.";
    private static final String DECOMPOSITION_SYSTEM_PROMPT = "You are an engineering task decomposition assistant. "
            + "Break one bug or feature into the smallest useful implementation stories. "
            + "Optimize for PR safety and reviewability, prefer independently testable stories, "
            + "prefer stories likely under 30000-char patches, avoid broad/vague tasks. "
            + "Include acceptanceCriteria, affectedComponents, estimatedSize, and prSafety notes. "
            + "Return valid JSON only with keys: requestId, decompositionComplete, stories.";

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String model;
    private final String decompositionModel;
    private final boolean configured;

    public IntakeOpenAiClient(ObjectMapper objectMapper,
                              @Value("${app.intake.openai.api-key:}") String apiKey,
                              @Value("${app.intake.model:gpt-4.1-mini}") String model,
                              @Value("${app.intake.decomposition.model:${app.intake.model:gpt-4.1-mini}}") String decompositionModel) {
        this.objectMapper = objectMapper;
        this.model = model;
        this.decompositionModel = decompositionModel;
        if (!StringUtils.hasText(apiKey)) {
            this.configured = false;
            this.restClient = null;
        } else {
            this.configured = true;
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
    }

    public NormalizedIntakeResult collectIntake(List<ChatMessage> messages) {
        if (!configured || restClient == null) {
            throw new IllegalStateException("OpenAI API key is not configured.");
        }
        List<Map<String, String>> requestMessages = new ArrayList<>();
        requestMessages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));

        if (messages != null) {
            List<Map<String, String>> filtered = new ArrayList<>();
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
                    filtered.add(Map.of("role", role, "content", truncateContent(content)));
                } else {
                    log.debug("Skipping empty chat message content for role {}", role);
                }
            }
            if (filtered.size() <= MAX_HISTORY_MESSAGES) {
                requestMessages.addAll(filtered);
            } else {
                // Preserve earliest non-empty context plus the most recent history.
                requestMessages.add(filtered.get(0));
                int tailCount = Math.max(0, MAX_HISTORY_MESSAGES - 1);
                int tailStart = Math.max(1, filtered.size() - tailCount);
                requestMessages.addAll(filtered.subList(tailStart, filtered.size()));
            }
        }
        if (requestMessages.size() > MAX_TOTAL_MESSAGES) {
            requestMessages = new ArrayList<>(requestMessages.subList(0, MAX_TOTAL_MESSAGES));
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
                return fallbackResult("Intake service model settings are currently incompatible. Please try again shortly.");
            }
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                return fallbackResult("Intake authorization is currently failing. Please try again shortly.");
            }
            if (ex.getStatusCode().value() == 429) {
                return fallbackResult("OpenAI is rate-limiting requests right now. Please retry shortly.");
            }
            return fallbackResult("Intake service is temporarily unavailable. Please try again shortly.");
        } catch (Exception ex) {
            log.warn("OpenAI intake request failed: {}", ex.getMessage());
            return fallbackResult();
        }
    }

    public DecompositionResponse decompose(String requestId, StructuredIntakeData structuredData) {
        String normalizedRequestId = requestId != null ? requestId.trim() : null;
        String safeRequestId = safeRequestId(normalizedRequestId);
        if (!StringUtils.hasText(normalizedRequestId)) {
            log.warn("Decomposition requestId is blank; returning safe fallback");
            return fallbackDecompositionResult(safeRequestId);
        }
        if (!isValidRequestId(normalizedRequestId)) {
            log.warn("Decomposition requestId failed validation; returning safe fallback");
            return fallbackDecompositionResult(safeRequestId);
        }
        if (structuredData == null) {
            return fallbackDecompositionResult(safeRequestId);
        }
        if (!configured || restClient == null) {
            return fallbackDecompositionResult(safeRequestId);
        }

        List<Map<String, String>> requestMessages = new ArrayList<>();
        requestMessages.add(Map.of("role", "system", "content", DECOMPOSITION_SYSTEM_PROMPT));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", normalizedRequestId);
        try {
            String payloadJson = null;
            int payloadBytes = Integer.MAX_VALUE;
            int[] fieldLimits = {
                    MAX_DECOMPOSITION_FIELD_CHARS,
                    MAX_DECOMPOSITION_FALLBACK_FIELD_CHARS,
                    250
            };
            int[] componentLimits = {
                    MAX_DECOMPOSITION_COMPONENTS,
                    MAX_DECOMPOSITION_FALLBACK_COMPONENTS,
                    5
            };
            for (int i = 0; i < fieldLimits.length; i++) {
                payload.put("structuredData",
                        normalizeStructuredDataForDecomposition(
                                structuredData,
                                fieldLimits[i],
                                componentLimits[i]
                        ));
                payloadJson = objectMapper.writeValueAsString(payload);
                payloadBytes = payloadJson.getBytes(StandardCharsets.UTF_8).length;
                if (payloadBytes <= MAX_DECOMPOSITION_PAYLOAD_BYTES) {
                    break;
                }
            }
            if (payloadBytes > MAX_DECOMPOSITION_PAYLOAD_BYTES) {
                log.warn("Decomposition payload too large for requestId={}, bytes={}", normalizedRequestId, payloadBytes);
                return fallbackDecompositionResult(safeRequestId);
            }
            requestMessages.add(Map.of("role", "user", "content", payloadJson));
        } catch (Exception ex) {
            log.warn("Failed to serialize decomposition payload", ex);
            return fallbackDecompositionResult(safeRequestId);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", decompositionModel);
        body.put("messages", requestMessages);
        body.put("temperature", 0.1);
        body.put("response_format", Map.of("type", "json_object"));

        try {
            String requestJson = objectMapper.writeValueAsString(body);
            int requestBytes = requestJson.getBytes(StandardCharsets.UTF_8).length;
            if (requestBytes > MAX_DECOMPOSITION_REQUEST_BYTES) {
                log.warn("Decomposition request too large for requestId={}, bytes={}", normalizedRequestId, requestBytes);
                return fallbackDecompositionResult(safeRequestId);
            }
            String raw = restClient.post()
                    .uri("/chat/completions")
                    .body(requestJson)
                    .retrieve()
                    .body(String.class);
            return parseDecompositionResult(raw, safeRequestId);
        } catch (Exception ex) {
            log.warn("OpenAI decomposition request failed: {}", ex.getClass().getSimpleName());
            return fallbackDecompositionResult(safeRequestId);
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

    private DecompositionResponse parseDecompositionResult(String rawResponse, String requestId) {
        try {
            if (!StringUtils.hasText(rawResponse)) {
                log.warn("OpenAI decomposition response body was empty");
                return fallbackDecompositionResult(requestId);
            }
            if (rawResponse.getBytes(StandardCharsets.UTF_8).length > MAX_DECOMPOSITION_RESPONSE_BYTES) {
                log.warn("OpenAI decomposition response too large for requestId={}", requestId);
                return fallbackDecompositionResult(requestId);
            }
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode decompositionJson = extractDecompositionJson(root);
            if (decompositionJson == null || decompositionJson.isNull() || decompositionJson.isMissingNode()) {
                log.warn("OpenAI decomposition JSON extraction failed for configured chat response shape");
                return fallbackDecompositionResult(requestId);
            }
            if (!isDecompositionPayload(decompositionJson)) {
                log.warn("OpenAI decomposition JSON does not match expected payload shape");
                return fallbackDecompositionResult(requestId);
            }

            DecompositionResponse response = new DecompositionResponse();
            response.setRequestId(requestId);
            response.setDecompositionComplete(toBoolean(decompositionJson.path("decompositionComplete")));

            List<DecompositionStory> stories = new ArrayList<>();
            JsonNode storiesNode = decompositionJson.path("stories");
            if (storiesNode.isArray()) {
                for (JsonNode item : storiesNode) {
                    if (item == null || !item.isObject()) {
                        continue;
                    }
                    stories.add(toDecompositionStory(item));
                }
            }
            response.setStories(stories);
            return response;
        } catch (Exception ex) {
            log.warn("Failed to parse OpenAI decomposition response JSON", ex);
            return fallbackDecompositionResult(requestId);
        }
    }

    private DecompositionStory toDecompositionStory(JsonNode node) {
        DecompositionStory story = new DecompositionStory();
        story.setStoryId(truncateForDecomposition(node.path("storyId").asText(null)));
        story.setTitle(truncateForDecomposition(node.path("title").asText(null)));
        story.setDescription(truncateForDecomposition(node.path("description").asText(null)));
        story.setEstimatedSize(truncateForDecomposition(node.path("estimatedSize").asText(null)));

        List<String> acceptanceCriteria = new ArrayList<>();
        JsonNode criteriaNode = node.path("acceptanceCriteria");
        if (criteriaNode.isArray()) {
            for (JsonNode item : criteriaNode) {
                String value = blankToNull(item.asText(null));
                if (value != null) {
                    acceptanceCriteria.add(truncateForDecomposition(value));
                }
            }
        }
        story.setAcceptanceCriteria(acceptanceCriteria);

        List<String> affectedComponents = new ArrayList<>();
        JsonNode componentsNode = node.path("affectedComponents");
        if (componentsNode.isArray()) {
            for (JsonNode item : componentsNode) {
                String value = blankToNull(item.asText(null));
                if (value != null) {
                    affectedComponents.add(truncateForDecomposition(value));
                }
            }
        }
        story.setAffectedComponents(affectedComponents);
        story.setPrSafety(toPrSafety(node.path("prSafety")));
        return story;
    }

    private PrSafety toPrSafety(JsonNode node) {
        PrSafety prSafety = new PrSafety();
        if (node == null || node.isMissingNode() || node.isNull() || !node.isObject()) {
            prSafety.setTarget("under-30000-char-patch");
            prSafety.setNotes(null);
            return prSafety;
        }
        String target = blankToNull(node.path("target").asText(null));
        prSafety.setTarget(target != null ? truncateForDecomposition(target) : "under-30000-char-patch");
        prSafety.setNotes(truncateForDecomposition(node.path("notes").asText(null)));
        return prSafety;
    }

    private JsonNode extractDecompositionJson(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray()) {
            return null;
        }
        for (JsonNode choice : choices) {
            JsonNode messageNode = choice.path("message");
            if (!messageNode.isObject()) {
                continue;
            }
            JsonNode contentNode = messageNode.path("content");
            if (contentNode.isObject() && isDecompositionPayload(contentNode)) {
                return contentNode;
            }
            JsonNode fromContent = parseJsonFromContentNode(contentNode);
            if (fromContent != null && fromContent.isObject() && isDecompositionPayload(fromContent)) {
                return fromContent;
            }
            JsonNode fromText = parseJsonFromContent(contentNode.asText(""));
            if (fromText != null && fromText.isObject() && isDecompositionPayload(fromText)) {
                return fromText;
            }
            JsonNode parsedNode = messageNode.path("parsed");
            if (parsedNode != null && parsedNode.isObject() && isDecompositionPayload(parsedNode)) {
                return parsedNode;
            }
        }
        return null;
    }

    private boolean isDecompositionPayload(JsonNode node) {
        if (!node.isObject()) {
            return false;
        }
        JsonNode completionNode = node.path("decompositionComplete");
        if (!completionNode.isMissingNode()
                && !completionNode.isNull()
                && !completionNode.isBoolean()
                && !completionNode.isNumber()
                && !completionNode.isTextual()) {
            return false;
        }
        JsonNode storiesNode = node.path("stories");
        if (!storiesNode.isMissingNode() && !storiesNode.isNull() && !storiesNode.isArray()) {
            return false;
        }
        if (toBoolean(completionNode) && !storiesNode.isArray()) {
            return false;
        }
        if (storiesNode.isArray()) {
            for (JsonNode storyNode : storiesNode) {
                if (storyNode == null || storyNode.isNull()) {
                    continue;
                }
                if (!storyNode.isObject()) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isValidRequestId(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return false;
        }
        if (requestId.length() > 256) {
            return false;
        }
        for (int i = 0; i < requestId.length(); i++) {
            char ch = requestId.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '-' && ch != '_' && ch != ':' && ch != '.') {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> normalizeStructuredDataForDecomposition(StructuredIntakeData structuredData,
                                                                        int maxFieldChars,
                                                                        int maxComponents) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        putIfNotNull(normalized, "type", blankToNull(structuredData.getType()));
        putIfNotNull(normalized, "title", truncateForDecomposition(structuredData.getTitle(), maxFieldChars));
        putIfNotNull(normalized, "description", truncateForDecomposition(structuredData.getDescription(), maxFieldChars));
        putIfNotNull(normalized, "stepsToReproduce", truncateForDecomposition(structuredData.getStepsToReproduce(), maxFieldChars));
        putIfNotNull(normalized, "expectedBehavior", truncateForDecomposition(structuredData.getExpectedBehavior(), maxFieldChars));
        putIfNotNull(normalized, "priority", blankToNull(structuredData.getPriority()));

        List<String> affectedComponents = new ArrayList<>();
        if (structuredData.getAffectedComponents() != null) {
            for (String component : structuredData.getAffectedComponents()) {
                String value = blankToNull(component);
                if (value != null) {
                    affectedComponents.add(truncateForDecomposition(value, maxFieldChars));
                    if (affectedComponents.size() >= maxComponents) {
                        break;
                    }
                }
            }
        }
        if (!affectedComponents.isEmpty()) {
            normalized.put("affectedComponents", affectedComponents);
        }
        return normalized;
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String truncateForDecomposition(String value) {
        return truncateForDecomposition(value, MAX_DECOMPOSITION_FIELD_CHARS);
    }

    private String truncateForDecomposition(String value, int maxChars) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        int boundary = safeBoundary(trimmed, maxChars);
        int clampedBoundary = Math.max(1, Math.min(boundary, trimmed.length()));
        return trimmed.substring(0, clampedBoundary);
    }

    private boolean toBoolean(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isBoolean()) {
            return node.asBoolean(false);
        }
        if (node.isTextual()) {
            String value = blankToNull(node.asText(null));
            if (value == null) {
                return false;
            }
            return Boolean.parseBoolean(value);
        }
        if (node.isNumber()) {
            return node.asInt(0) != 0;
        }
        return false;
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
            String value = contentNode.path("value").asText("");
            if (StringUtils.hasText(value)) {
                return parseJsonFromContent(value);
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
            int firstBrace = jsonOnly.indexOf('{');
            int lastBrace = jsonOnly.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                String candidate = jsonOnly.substring(firstBrace, lastBrace + 1);
                try {
                    return objectMapper.readTree(candidate);
                } catch (Exception ignored) {
                    // continue to balanced-object extraction fallback
                }
            }
            String firstObject = extractFirstJsonObject(jsonOnly);
            if (firstObject != null) {
                try {
                    return objectMapper.readTree(firstObject);
                } catch (Exception ignored) {
                    return null;
                }
            }
            return null;
        }
    }

    private String extractFirstJsonObject(String content) {
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (ch == '}' && depth > 0) {
                depth--;
                if (depth == 0 && start >= 0) {
                    return content.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private String safeRequestId(String requestId) {
        return StringUtils.hasText(requestId) ? requestId.trim() : "unknown-request";
    }

    private String blankToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    String truncateContent(String content) {
        int rawCap = MAX_CONTENT_CHARS + TRUNCATION_BOUNDARY_WINDOW;
        if (content.length() > rawCap) {
            content = content.substring(0, rawCap);
        }
        if (content.length() <= MAX_CONTENT_CHARS) {
            return content;
        }
        int cap = MAX_CONTENT_CHARS;
        int boundary = findBoundary(content, cap);
        int preferredBoundary = boundary >= (cap - 20) ? boundary : cap;
        int safeBoundary = safeBoundary(content, preferredBoundary);
        int cappedBoundary = Math.max(1, Math.min(safeBoundary, cap));
        return content.substring(0, cappedBoundary);
    }

    int findBoundary(String content, int maxChars) {
        int effectiveMax = Math.max(1, Math.min(maxChars, content.length()));
        int minBoundary = Math.max(1, effectiveMax - TRUNCATION_BOUNDARY_WINDOW);
        for (int i = effectiveMax; i >= minBoundary; i--) {
            char ch = content.charAt(i - 1);
            if (ch == '\n' || ch == '}' || ch == '.' || ch == '!' || ch == '?' || Character.isWhitespace(ch)) {
                return i;
            }
        }
        return effectiveMax;
    }

    int safeBoundary(String content, int boundary) {
        if (boundary <= 0 || boundary >= content.length()) {
            return Math.max(0, Math.min(boundary, content.length()));
        }
        char prev = content.charAt(boundary - 1);
        char next = content.charAt(boundary);
        if (Character.isHighSurrogate(prev) && Character.isLowSurrogate(next)) {
            return boundary - 1;
        }
        return boundary;
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

    private DecompositionResponse fallbackDecompositionResult(String requestId) {
        DecompositionResponse fallback = new DecompositionResponse();
        fallback.setRequestId(requestId);
        fallback.setDecompositionComplete(false);
        fallback.setStories(Collections.emptyList());
        return fallback;
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
