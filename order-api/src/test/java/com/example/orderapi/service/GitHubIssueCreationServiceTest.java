package com.example.orderapi.service;

import com.example.orderapi.dto.DecisionTraceEventResponse;
import com.example.orderapi.dto.DecompositionStory;
import com.example.orderapi.dto.GitHubIssueCreateRequest;
import com.example.orderapi.dto.GitHubIssueCreateResponse;
import com.example.orderapi.dto.GitHubIssueSummary;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
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
        verify(intakeTraceabilityAgent, times(1))
                .recordGitHubSummaryCommentResult(eq("trace-123"), eq("req-123"), eq("feature"), eq(2), eq(2), argThat(List::isEmpty));
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
        verify(intakeTraceabilityAgent, times(1))
                .recordGitHubSummaryCommentResult(eq("trace-789"), eq("req-789"), eq("bug"), eq(2), eq(1), eq(List.of(201L)));
    }

    @Test
    void createFromDecomposition_usesLatestRationaleByTimestampWhenEventsAreUnordered() {
        GitHubIssueClientService gitHubIssueClientService = mock(GitHubIssueClientService.class);
        FileAuditLogService fileAuditLogService = mock(FileAuditLogService.class);
        IntakeTraceabilityAgent intakeTraceabilityAgent = mock(IntakeTraceabilityAgent.class);
        GitHubIssueCreationService service = new GitHubIssueCreationService(
                gitHubIssueClientService,
                fileAuditLogService,
                intakeTraceabilityAgent,
                new TraceabilityGitHubSummaryCommentBuilder()
        );

        when(intakeTraceabilityAgent.resolveTraceId(eq("trace-456"), eq("req-456"))).thenReturn("trace-456");

        List<DecisionTraceEventResponse> unorderedEvents = new ArrayList<>();
        unorderedEvents.add(rationaleEvent(
                "Older rationale from initial pass.",
                "intake.classification.completed",
                "2026-04-16T09:00:00Z"
        ));
        unorderedEvents.add(rationaleEvent(
                "Newer rationale from refined classification.",
                "intake.classification.completed",
                "2026-04-16T09:05:00Z"
        ));
        when(intakeTraceabilityAgent.readTraceEvents("trace-456")).thenReturn(unorderedEvents);
        when(gitHubIssueClientService.createIssueForStory(eq("feature"), any(DecompositionStory.class)))
                .thenReturn(issue(301L));

        GitHubIssueCreateResponse response = service.createFromDecomposition(request("trace-456", "feature", List.of(
                story("story-1", "First")
        )));

        assertTrue(response.isIssuesCreated());
        verify(gitHubIssueClientService, times(1))
                .addIssueComment(anyLong(), contains("- Rationale summary: Newer rationale from refined classification."));
        verify(intakeTraceabilityAgent, times(1))
                .recordGitHubSummaryCommentResult(eq("trace-456"), eq("req-456"), eq("feature"), eq(1), eq(1), argThat(List::isEmpty));
    }

    @Test
    void createFromDecomposition_doesNotFailWhenRestStyleHttpExceptionOccursWhilePostingComment() {
        GitHubIssueClientService gitHubIssueClientService = mock(GitHubIssueClientService.class);
        FileAuditLogService fileAuditLogService = mock(FileAuditLogService.class);
        IntakeTraceabilityAgent intakeTraceabilityAgent = mock(IntakeTraceabilityAgent.class);
        GitHubIssueCreationService service = new GitHubIssueCreationService(
                gitHubIssueClientService,
                fileAuditLogService,
                intakeTraceabilityAgent,
                new TraceabilityGitHubSummaryCommentBuilder()
        );

        when(intakeTraceabilityAgent.resolveTraceId(eq("trace-999"), eq("req-999"))).thenReturn("trace-999");
        when(intakeTraceabilityAgent.readTraceEvents("trace-999")).thenReturn(List.of());
        when(gitHubIssueClientService.createIssueForStory(eq("bug"), any(DecompositionStory.class)))
                .thenReturn(issue(401L));
        doThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "bad request"))
                .when(gitHubIssueClientService)
                .addIssueComment(anyLong(), any(String.class));

        GitHubIssueCreateResponse response = service.createFromDecomposition(request("trace-999", "bug", List.of(
                story("story-1", "First")
        )));

        assertTrue(response.isIssuesCreated());
        assertEquals(1, response.getIssues().size());
        verify(gitHubIssueClientService, times(1)).addIssueComment(anyLong(), contains("- Trace ID: `trace-999`"));
        verify(intakeTraceabilityAgent, times(1))
                .recordGitHubSummaryCommentResult(eq("trace-999"), eq("req-999"), eq("bug"), eq(1), eq(0), eq(List.of(401L)));
    }

    @Test
    void createFromDecomposition_recordsCommentFailureWhenIssueNumberMissing() {
        GitHubIssueClientService gitHubIssueClientService = mock(GitHubIssueClientService.class);
        FileAuditLogService fileAuditLogService = mock(FileAuditLogService.class);
        IntakeTraceabilityAgent intakeTraceabilityAgent = mock(IntakeTraceabilityAgent.class);
        GitHubIssueCreationService service = new GitHubIssueCreationService(
                gitHubIssueClientService,
                fileAuditLogService,
                intakeTraceabilityAgent,
                new TraceabilityGitHubSummaryCommentBuilder()
        );

        when(intakeTraceabilityAgent.resolveTraceId(eq("trace-missing-number"), eq("req-number")))
                .thenReturn("trace-missing-number");
        when(intakeTraceabilityAgent.readTraceEvents("trace-missing-number")).thenReturn(List.of());

        GitHubIssueSummary missingNumberIssue = new GitHubIssueSummary();
        missingNumberIssue.setIssueNumber(0L);
        missingNumberIssue.setTitle("Issue without number");
        when(gitHubIssueClientService.createIssueForStory(eq("feature"), any(DecompositionStory.class)))
                .thenReturn(missingNumberIssue);

        GitHubIssueCreateResponse response = service.createFromDecomposition(request(
                "trace-missing-number",
                "feature",
                List.of(story("story-1", "First"))
        ));

        assertTrue(response.isIssuesCreated());
        verify(gitHubIssueClientService, times(0)).addIssueComment(anyLong(), any(String.class));
        verify(intakeTraceabilityAgent, times(1))
                .recordGitHubSummaryCommentResult(eq("trace-missing-number"), eq("req-number"), eq("feature"), eq(1), eq(0), eq(List.of()));
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
        return rationaleEvent(rationale, "intake.classification.completed", "2026-04-16T09:00:00Z");
    }

    private static DecisionTraceEventResponse rationaleEvent(String rationale, String eventType, String timestamp) {
        DecisionTraceEventResponse event = new DecisionTraceEventResponse();
        event.setEventType(eventType);
        event.setTimestamp(timestamp);
        event.setDecisionMetadata(new LinkedHashMap<>(java.util.Map.of("rationaleSummary", rationale)));
        return event;
    }
}
