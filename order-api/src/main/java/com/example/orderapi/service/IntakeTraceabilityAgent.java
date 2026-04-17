package com.example.orderapi.service;

import com.example.orderapi.dto.DecisionTraceEventResponse;
import com.example.orderapi.dto.DecompositionResponse;
import com.example.orderapi.dto.GitHubIssueSummary;
import com.example.orderapi.dto.StructuredIntakeData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class IntakeTraceabilityAgent {

    private static final Logger log = LoggerFactory.getLogger(IntakeTraceabilityAgent.class);

    private final ObjectMapper objectMapper;
    private final Path traceLogPath;

    public IntakeTraceabilityAgent(
            ObjectMapper objectMapper,
            @Value("${app.intake.traceability.log-path:traceability/audit/decision-trace.jsonl}") String traceLogPath
    ) {
        this.objectMapper = objectMapper;
        this.traceLogPath = Paths.get(traceLogPath).toAbsolutePath().normalize();
    }

    public String resolveTraceId(String traceId, String requestId) {
        if (StringUtils.hasText(traceId)) {
            return traceId.trim();
        }
        if (StringUtils.hasText(requestId)) {
            return "trace-" + requestId.trim();
        }
        return "trace-" + UUID.randomUUID().toString().replace("-", "");
    }

    public void recordIntakeSessionStart(String traceId, String requestId, int messageCount) {
        appendEvent(
                traceId,
                requestId,
                "intake.session.started",
                "recorded",
                "intake-chat-service",
                "Started intake session capture.",
                Map.of("rationaleSummary", "Capture session context before intake analysis."),
                Map.of("messageCount", messageCount),
                Collections.emptyMap(),
                governanceMetadata()
        );
    }

    public void recordClassification(String traceId, String requestId, StructuredIntakeData structuredData) {
        String type = structuredData != null && StringUtils.hasText(structuredData.getType())
                ? structuredData.getType().trim().toLowerCase()
                : "unknown";
        String status = ("bug".equals(type) || "feature".equals(type)) ? "accepted" : "pending";
        appendEvent(
                traceId,
                requestId,
                "intake.classification.completed",
                status,
                "intake-chat-service",
                "Classified intake as " + type + ".",
                Map.of("classifiedType", type,
                        "rationaleSummary", "Classification was inferred from structured intake type."),
                Map.of("type", type),
                Collections.emptyMap(),
                governanceMetadata()
        );
    }

    public void recordStructuredIntake(String traceId, String requestId, StructuredIntakeData structuredData) {
        boolean hasTitle = structuredData != null && StringUtils.hasText(structuredData.getTitle());
        boolean hasDescription = structuredData != null && StringUtils.hasText(structuredData.getDescription());
        String status = (hasTitle && hasDescription) ? "accepted" : "pending";

        Map<String, Object> inputSummary = new LinkedHashMap<>();
        inputSummary.put("type", safeText(structuredData != null ? structuredData.getType() : null));
        inputSummary.put("title", safeText(structuredData != null ? structuredData.getTitle() : null));
        inputSummary.put("priority", safeText(structuredData != null ? structuredData.getPriority() : null));
        inputSummary.put("affectedComponents",
                structuredData != null && structuredData.getAffectedComponents() != null
                        ? structuredData.getAffectedComponents()
                        : Collections.emptyList());

        appendEvent(
                traceId,
                requestId,
                "intake.structured-data.captured",
                status,
                "intake-chat-service",
                "Captured structured intake payload.",
                Map.of("rationaleSummary", "Structured intake is required before decomposition and issue generation."),
                inputSummary,
                Collections.emptyMap(),
                governanceMetadata()
        );
    }

    public void recordIntakeFailure(String traceId, String requestId, String error) {
        appendEvent(
                traceId,
                requestId,
                "intake.session.failed",
                "failed",
                "intake-chat-service",
                "Intake session failed and fallback handling was triggered.",
                Map.of("rationaleSummary", "Failure was captured to preserve auditability of intake decisions."),
                Collections.emptyMap(),
                Map.of("error", safeText(error)),
                governanceMetadata()
        );
    }

    public void recordDecomposition(String traceId, String requestId, DecompositionResponse response, String error) {
        boolean completed = response != null && response.isDecompositionComplete();
        List<?> stories = response != null && response.getStories() != null ? response.getStories() : Collections.emptyList();
        String eventType = completed ? "intake.decomposition.completed" : "intake.decomposition.failed";
        String status = completed ? "completed" : "failed";

        appendEvent(
                traceId,
                requestId,
                eventType,
                status,
                "decomposition-service",
                completed
                        ? "Decomposition produced actionable stories."
                        : "Decomposition did not complete successfully.",
                Map.of("rationaleSummary", "Decomposition decides whether intake can progress to GitHub issue generation."),
                Collections.emptyMap(),
                Map.of("storyCount", stories.size(), "error", safeText(error)),
                governanceMetadata()
        );
    }

    public void recordGitHubPayloadPrepared(String traceId, String requestId, String sourceType, int storyCount) {
        appendEvent(
                traceId,
                requestId,
                "intake.github.payload.prepared",
                "recorded",
                "github-issue-creation-service",
                "Prepared GitHub issue payload from decomposition stories.",
                Map.of("rationaleSummary", "Payload preparation maps structured stories into issue-ready fields."),
                Map.of("sourceType", safeText(sourceType), "storyCount", storyCount),
                Collections.emptyMap(),
                governanceMetadata()
        );
    }

    public void recordGitHubIssueCreationResult(String traceId,
                                                String requestId,
                                                String sourceType,
                                                List<GitHubIssueSummary> issues,
                                                String error) {
        List<GitHubIssueSummary> safeIssues = issues != null ? issues : Collections.emptyList();
        boolean success = safeText(error).isEmpty();
        List<String> issueLinks = safeIssues.stream()
                .map(GitHubIssueSummary::getIssueUrl)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toList());

        appendEvent(
                traceId,
                requestId,
                success ? "intake.github.issue-creation.completed" : "intake.github.issue-creation.failed",
                success ? "completed" : "failed",
                "github-issue-creation-service",
                success
                        ? "Created GitHub issues from decomposed intake stories."
                        : "GitHub issue creation failed.",
                Map.of("sourceType", safeText(sourceType),
                        "rationaleSummary", "Issue creation outcome determines whether intake exits as actionable artifacts."),
                Collections.emptyMap(),
                Map.of("issueCount", safeIssues.size(), "issueLinks", issueLinks, "error", safeText(error)),
                governanceMetadata()
        );
    }

    public void recordGitHubSummaryCommentResult(String traceId,
                                                 String requestId,
                                                 String sourceType,
                                                 int issueCount,
                                                 int commentedIssueCount,
                                                 List<Long> failedIssueNumbers) {
        if (issueCount <= 0) {
            return;
        }
        List<Long> safeFailures = failedIssueNumbers != null ? failedIssueNumbers : Collections.emptyList();
        boolean allSucceeded = safeFailures.isEmpty() && commentedIssueCount >= issueCount;
        int failedIssueCount = safeFailures.size();

        appendEvent(
                traceId,
                requestId,
                allSucceeded ? "intake.github.summary-comment.completed" : "intake.github.summary-comment.failed",
                allSucceeded ? "completed" : "failed",
                "github-issue-creation-service",
                allSucceeded
                        ? "Posted engineer-facing trace summary comments to created GitHub issues."
                        : "One or more GitHub trace summary comments could not be posted.",
                Map.of("sourceType", safeText(sourceType),
                        "rationaleSummary", "Summary comments provide concise engineering context while keeping full trace detail in the decision trace log."),
                Collections.emptyMap(),
                Map.of(
                        "issueCount", issueCount,
                        "commentedIssueCount", commentedIssueCount,
                        "failedIssueCount", failedIssueCount,
                        "failedIssueNumbers", safeFailures
                ),
                governanceMetadata()
        );
    }

    public synchronized List<DecisionTraceEventResponse> readTraceEvents(String traceId) {
        if (!StringUtils.hasText(traceId) || !Files.exists(traceLogPath)) {
            return Collections.emptyList();
        }
        try {
            return Files.readAllLines(traceLogPath).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .map(this::parseTraceLine)
                    .filter(entry -> entry != null && traceId.equals(entry.getTraceId()))
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            log.warn("Failed reading traceability events for traceId={}", traceId, ex);
            return Collections.emptyList();
        }
    }

    private synchronized void appendEvent(String traceId,
                                          String requestId,
                                          String eventType,
                                          String status,
                                          String actor,
                                          String summary,
                                          Map<String, Object> decisionMetadata,
                                          Map<String, Object> inputSummary,
                                          Map<String, Object> artifactSummary,
                                          Map<String, Object> governanceMetadata) {
        try {
            Path parent = traceLogPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("traceId", safeText(traceId));
            entry.put("sessionId", safeText(requestId));
            entry.put("correlationId", UUID.randomUUID().toString());
            entry.put("eventType", safeText(eventType));
            entry.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
            entry.put("status", safeText(status));
            entry.put("actor", safeText(actor));
            entry.put("summary", safeText(summary));
            entry.put("decisionMetadata", decisionMetadata != null ? decisionMetadata : Collections.emptyMap());
            entry.put("inputSummary", inputSummary != null ? inputSummary : Collections.emptyMap());
            entry.put("artifactSummary", artifactSummary != null ? artifactSummary : Collections.emptyMap());
            entry.put("governanceMetadata", governanceMetadata != null ? governanceMetadata : governanceMetadata());

            String jsonLine = objectMapper.writeValueAsString(entry) + System.lineSeparator();
            Files.writeString(traceLogPath, jsonLine, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ex) {
            log.warn("Failed to append traceability event eventType={} traceId={}", safeText(eventType), safeText(traceId), ex);
        }
    }

    private Map<String, Object> governanceMetadata() {
        return Map.of(
                "piiSafe", true,
                "rawReasoningStored", false,
                "capturePolicy", "summary-only"
        );
    }

    private String safeText(String value) {
        return value != null ? value : "";
    }

    private DecisionTraceEventResponse parseTraceLine(String line) {
        try {
            Map<String, Object> record = objectMapper.readValue(line, new TypeReference<Map<String, Object>>() {});
            DecisionTraceEventResponse response = new DecisionTraceEventResponse();
            response.setTraceId(safeText(asString(record.get("traceId"))));
            response.setSessionId(safeText(asString(record.get("sessionId"))));
            response.setCorrelationId(safeText(asString(record.get("correlationId"))));
            response.setEventType(safeText(asString(record.get("eventType"))));
            response.setTimestamp(safeText(asString(record.get("timestamp"))));
            response.setStatus(safeText(asString(record.get("status"))));
            response.setActor(safeText(asString(record.get("actor"))));
            response.setSummary(safeText(asString(record.get("summary"))));
            response.setDecisionMetadata(asMap(record.get("decisionMetadata")));
            response.setInputSummary(asMap(record.get("inputSummary")));
            response.setArtifactSummary(asMap(record.get("artifactSummary")));
            response.setGovernanceMetadata(asMap(record.get("governanceMetadata")));
            return response;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, entryValue) -> normalized.put(String.valueOf(key), entryValue));
            return normalized;
        }
        return Collections.emptyMap();
    }
}
