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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
        for (DecisionTraceEventResponse event : events) {
            if (event == null) {
                continue;
            }
            Map<String, Object> decisionMetadata = event.getDecisionMetadata();
            if (decisionMetadata == null) {
                continue;
            }
            Object rationale = decisionMetadata.get("rationaleSummary");
            if (rationale != null && StringUtils.hasText(String.valueOf(rationale))) {
                return String.valueOf(rationale).trim();
            }
        }
        return "";
    }
}
