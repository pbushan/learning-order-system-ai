package com.example.orderapi.service;

import com.example.orderapi.dto.DecisionTraceEventResponse;
import com.example.orderapi.dto.DecompositionStory;
import com.example.orderapi.dto.GitHubIssueCreateRequest;
import com.example.orderapi.dto.GitHubIssueCreateResponse;
import com.example.orderapi.dto.GitHubIssueSummary;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubIssueCreationServiceTest {

    @Test
    void createFromDecomposition_postsTraceSummaryCommentForEachCreatedIssue() {
        GitHubIssueClientService gitHubIssueClientService = mock(GitHubIssueClientService.class);
        FileAuditLogService fileAuditLogService = mock(FileAuditLogService.class);
        IntakeTraceabilityAgent intakeTraceabilityAgent = mock(IntakeTraceabilityAgent.class);
        GitHubIssueCreationService service = new GitHubIssueCreationService(
                gitHubIssueClientService,
                fileAuditLogService,
                intakeTraceabilityAgent,
                new TraceabilityGitHubSummaryCommentBuilder()
        );

        when(intakeTraceabilityAgent.resolveTraceId(eq("trace-123"), eq("req-123"))).thenReturn("trace-123");
        when(intakeTraceabilityAgent.readTraceEvents("trace-123")).thenReturn(List.of(rationaleEvent("Classification inferred from intake semantics.")));
        when(gitHubIssueClientService.createIssueForStory(eq("feature"), any(DecompositionStory.class)))
                .thenReturn(issue(101L), issue(102L));

        GitHubIssueCreateResponse response = service.createFromDecomposition(request("trace-123", "feature", List.of(
                story("story-1", "First"),
                story("story-2", "Second")
        )));

        assertTrue(response.isIssuesCreated());
        assertEquals(2, response.getIssues().size());
        verify(gitHubIssueClientService, times(2)).addIssueComment(anyLong(), contains("- Classification: `feature`"));
        verify(gitHubIssueClientService, times(2)).addIssueComment(anyLong(), contains("- Decomposed multi-issue set: yes (2 issues)"));
        verify(gitHubIssueClientService, times(2)).addIssueComment(anyLong(), contains("- Trace ID: `trace-123`"));
    }

    @Test
    void createFromDecomposition_doesNotFailWhenTraceSummaryCommentPostFails() {
        GitHubIssueClientService gitHubIssueClientService = mock(GitHubIssueClientService.class);
        FileAuditLogService fileAuditLogService = mock(FileAuditLogService.class);
        IntakeTraceabilityAgent intakeTraceabilityAgent = mock(IntakeTraceabilityAgent.class);
        GitHubIssueCreationService service = new GitHubIssueCreationService(
                gitHubIssueClientService,
                fileAuditLogService,
                intakeTraceabilityAgent,
                new TraceabilityGitHubSummaryCommentBuilder()
        );

        when(intakeTraceabilityAgent.resolveTraceId(eq("trace-789"), eq("req-789"))).thenReturn("trace-789");
        when(intakeTraceabilityAgent.readTraceEvents("trace-789")).thenReturn(List.of());
        when(gitHubIssueClientService.createIssueForStory(eq("bug"), any(DecompositionStory.class)))
                .thenReturn(issue(201L), issue(202L));
        doThrow(new IllegalStateException("comment endpoint unavailable"))
                .doNothing()
                .when(gitHubIssueClientService)
                .addIssueComment(anyLong(), any(String.class));

        GitHubIssueCreateResponse response = service.createFromDecomposition(request("trace-789", "bug", List.of(
                story("story-1", "First"),
                story("story-2", "Second")
        )));

        assertTrue(response.isIssuesCreated());
        assertEquals(2, response.getIssues().size());
        verify(gitHubIssueClientService, times(2)).createIssueForStory(eq("bug"), any(DecompositionStory.class));
        verify(gitHubIssueClientService, times(2)).addIssueComment(anyLong(), contains("- Trace ID: `trace-789`"));
    }

    private static GitHubIssueCreateRequest request(String traceId, String sourceType, List<DecompositionStory> stories) {
        GitHubIssueCreateRequest request = new GitHubIssueCreateRequest();
        request.setRequestId("req-" + traceId.substring(traceId.lastIndexOf('-') + 1));
        request.setTraceId(traceId);
        request.setSourceType(sourceType);
        request.setStories(stories);
        return request;
    }

    private static DecompositionStory story(String storyId, String titleSuffix) {
        DecompositionStory story = new DecompositionStory();
        story.setStoryId(storyId);
        story.setTitle("Story " + titleSuffix);
        story.setDescription("Description " + titleSuffix);
        return story;
    }

    private static GitHubIssueSummary issue(long issueNumber) {
        GitHubIssueSummary summary = new GitHubIssueSummary();
        summary.setIssueNumber(issueNumber);
        summary.setIssueUrl("https://example.test/issues/" + issueNumber);
        summary.setTitle("Issue " + issueNumber);
        return summary;
    }

    private static DecisionTraceEventResponse rationaleEvent(String rationale) {
        DecisionTraceEventResponse event = new DecisionTraceEventResponse();
        event.setDecisionMetadata(new LinkedHashMap<>(java.util.Map.of("rationaleSummary", rationale)));
        return event;
    }
}
