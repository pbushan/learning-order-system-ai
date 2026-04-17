package com.example.orderapi.service;

import com.example.orderapi.dto.DecompositionStory;
import com.example.orderapi.dto.DecisionTraceEventResponse;
import com.example.orderapi.dto.GitHubIssueCreateRequest;
import com.example.orderapi.dto.GitHubIssueCreateResponse;
import com.example.orderapi.dto.GitHubIssueSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Service
public class GitHubIssueCreationService {

    private static final Logger log = LoggerFactory.getLogger(GitHubIssueCreationService.class);

    private final GitHubIssueClientService gitHubIssueClientService;
    private final FileAuditLogService fileAuditLogService;
    private final IntakeTraceabilityAgent intakeTraceabilityAgent;
    private final TraceabilityGitHubSummaryCommentBuilder traceabilityGitHubSummaryCommentBuilder;

    public GitHubIssueCreationService(GitHubIssueClientService gitHubIssueClientService,
                                      FileAuditLogService fileAuditLogService,
                                      IntakeTraceabilityAgent intakeTraceabilityAgent,
                                      TraceabilityGitHubSummaryCommentBuilder traceabilityGitHubSummaryCommentBuilder) {
        this.gitHubIssueClientService = gitHubIssueClientService;
        this.fileAuditLogService = fileAuditLogService;
        this.intakeTraceabilityAgent = intakeTraceabilityAgent;
        this.traceabilityGitHubSummaryCommentBuilder = traceabilityGitHubSummaryCommentBuilder;
    }

    public GitHubIssueCreateResponse createFromDecomposition(GitHubIssueCreateRequest request) {
        String requestId = request != null && StringUtils.hasText(request.getRequestId())
                ? request.getRequestId().trim()
                : "unknown-request";
        String traceId = intakeTraceabilityAgent.resolveTraceId(request != null ? request.getTraceId() : null, requestId);
        String sourceType = request != null && StringUtils.hasText(request.getSourceType())
                ? request.getSourceType().trim()
                : "";
        List<DecompositionStory> stories = request != null && request.getStories() != null
                ? request.getStories()
                : Collections.emptyList();
        List<GitHubIssueSummary> issues = new ArrayList<>();

        try {
            validateRequest(request);
            sourceType = request.getSourceType().trim();
            stories = request.getStories();
            intakeTraceabilityAgent.recordGitHubPayloadPrepared(traceId, requestId, sourceType, stories.size());
            String rationaleSummary = resolveRationaleSummary(traceId);
            for (DecompositionStory story : stories) {
                GitHubIssueSummary issue = gitHubIssueClientService.createIssueForStory(sourceType, story);
                issue.setStoryId(story.getStoryId());
                issues.add(issue);
                safeAddTraceSummaryComment(issue, traceId, sourceType, stories.size(), rationaleSummary);
            }
            GitHubIssueCreateResponse response = new GitHubIssueCreateResponse();
            response.setRequestId(requestId);
            response.setTraceId(traceId);
            response.setIssuesCreated(!issues.isEmpty());
            response.setIssues(issues);

            safeAuditLog(requestId, sourceType, stories, issues, null);
            intakeTraceabilityAgent.recordGitHubIssueCreationResult(traceId, requestId, sourceType, issues, null);
            return response;
        } catch (Exception ex) {
            safeAuditLog(requestId, sourceType, stories, issues, ex.getMessage());
            intakeTraceabilityAgent.recordGitHubIssueCreationResult(traceId, requestId, sourceType, issues, ex.getMessage());
            throw ex;
        }
    }

    private void validateRequest(GitHubIssueCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.getRequestId())) {
            throw new IllegalArgumentException("requestId is required");
        }
        if (!StringUtils.hasText(request.getSourceType())) {
            throw new IllegalArgumentException("sourceType is required");
        }
        List<DecompositionStory> stories = request.getStories();
        if (stories == null || stories.isEmpty()) {
            throw new IllegalArgumentException("stories is required");
        }

        for (int i = 0; i < stories.size(); i++) {
            DecompositionStory story = stories.get(i);
            if (story == null || !StringUtils.hasText(story.getTitle())) {
                throw new IllegalArgumentException("stories[" + i + "].title is required");
            }
            if (!StringUtils.hasText(story.getDescription())) {
                throw new IllegalArgumentException("stories[" + i + "].description is required");
            }
        }
    }

    private void safeAuditLog(String requestId,
                              String sourceType,
                              List<DecompositionStory> stories,
                              List<GitHubIssueSummary> issues,
                              String error) {
        try {
            fileAuditLogService.logGitHubIssueCreationEntry(requestId, sourceType, stories, issues, error);
        } catch (Exception ignored) {
            // Do not fail issue creation due to audit logging.
        }
    }

    private void safeAddTraceSummaryComment(GitHubIssueSummary issue,
                                            String traceId,
                                            String sourceType,
                                            int issueCount,
                                            String rationaleSummary) {
        if (issue == null || issue.getIssueNumber() <= 0) {
            return;
        }

        try {
            String comment = traceabilityGitHubSummaryCommentBuilder.buildIssueTraceSummary(
                    traceId,
                    sourceType,
                    issueCount,
                    rationaleSummary
            );
            gitHubIssueClientService.addIssueComment(issue.getIssueNumber(), comment);
        } catch (Exception ex) {
            log.warn("Failed to add trace summary comment for issue={} traceId={}",
                    issue.getIssueNumber(), traceId, ex);
        }
    }

    private String resolveRationaleSummary(String traceId) {
        List<DecisionTraceEventResponse> events = intakeTraceabilityAgent.readTraceEvents(traceId);
        String preferred = selectLatestRationale(events, event -> {
            String eventType = event != null ? event.getEventType() : "";
            return "intake.classification.completed".equals(eventType)
                    || "intake.structured-data.captured".equals(eventType);
        });
        if (StringUtils.hasText(preferred)) {
            return preferred;
        }
        return selectLatestRationale(events, event -> true);
    }

    private String selectLatestRationale(List<DecisionTraceEventResponse> events,
                                         Predicate<DecisionTraceEventResponse> predicate) {
        if (events == null || events.isEmpty()) {
            return "";
        }

        return events.stream()
                .filter(event -> event != null && predicate.test(event))
                .map(event -> new RationaleCandidate(extractRationale(event), event.getTimestamp()))
                .filter(candidate -> StringUtils.hasText(candidate.rationale()))
                .max(Comparator.comparing(RationaleCandidate::parsedTimestamp)
                        .thenComparing(RationaleCandidate::rationale))
                .map(RationaleCandidate::rationale)
                .orElse("");
    }

    private String extractRationale(DecisionTraceEventResponse event) {
        Map<String, Object> decisionMetadata = event.getDecisionMetadata();
        if (decisionMetadata == null) {
            return "";
        }
        Object rationale = decisionMetadata.get("rationaleSummary");
        if (rationale == null || !StringUtils.hasText(String.valueOf(rationale))) {
            return "";
        }
        return String.valueOf(rationale).trim();
    }

    private record RationaleCandidate(String rationale, String timestamp) {
        private OffsetDateTime parsedTimestamp() {
            if (!StringUtils.hasText(timestamp)) {
                return OffsetDateTime.ofInstant(java.time.Instant.EPOCH, ZoneOffset.UTC);
            }
            try {
                return OffsetDateTime.parse(timestamp);
            } catch (DateTimeParseException ignored) {
                return OffsetDateTime.ofInstant(java.time.Instant.EPOCH, ZoneOffset.UTC);
            }
        }
    }
}
