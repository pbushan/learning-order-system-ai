package com.example.orderapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Step6PrReviewPollingServiceTest {

    @Mock
    private GitHubIssueClientService gitHubIssueClientService;

    @Mock
    private FileAuditLogService fileAuditLogService;

    @Mock
    private Step5IssueExecutionService step5IssueExecutionService;

    private Step6PrReviewPollingService service;

    @BeforeEach
    void setUp() {
        service = new Step6PrReviewPollingService(
                true,
                3,
                1,
                2,
                gitHubIssueClientService,
                step5IssueExecutionService,
                fileAuditLogService
        );
    }

    @Test
    void pollsExternalFeedbackAndPostsTerminalHandoff() {
        Map<String, Object> pr = Map.of(
                "number", 200L,
                "title", "Issue #200: Review feedback path",
                "headRefName", "codex/issue-200-feedback-test"
        );
        when(gitHubIssueClientService.listOpenPullRequests()).thenReturn(List.of(pr));
        when(gitHubIssueClientService.fetchIssueLabelNamesCaseInsensitive(200L)).thenReturn(Set.of());
        when(gitHubIssueClientService.getPullRequestReviews(200L))
                .thenReturn(List.of(Map.of("id", "r1", "author", "reviewer", "body", "typo fix", "url", "http://example")));
        when(gitHubIssueClientService.getPullRequestReviewComments(200L)).thenReturn(List.of());
        when(gitHubIssueClientService.getPullRequestIssueComments(200L)).thenReturn(List.of());

        service.pollManagedPullRequests();

        verify(gitHubIssueClientService, times(2)).addPullRequestComment(eq(200L), contains("Step 6"));
        verify(gitHubIssueClientService).addIssueLabel(200L, "step6-terminal");
    }

    @Test
    void postsSelfReviewFallbackWhenNoExternalFeedback() {
        Map<String, Object> pr = Map.of(
                "number", 201L,
                "title", "Issue #201: No feedback fallback",
                "headRefName", "codex/issue-201-no-feedback-test"
        );
        when(gitHubIssueClientService.listOpenPullRequests()).thenReturn(List.of(pr));
        when(gitHubIssueClientService.fetchIssueLabelNamesCaseInsensitive(201L)).thenReturn(Set.of());
        when(gitHubIssueClientService.getPullRequestReviews(201L)).thenReturn(List.of());
        when(gitHubIssueClientService.getPullRequestReviewComments(201L)).thenReturn(List.of());
        when(gitHubIssueClientService.getPullRequestIssueComments(201L)).thenReturn(List.of());

        service.pollManagedPullRequests();
        verify(gitHubIssueClientService, never()).addIssueLabel(201L, "step6-terminal");

        service.pollManagedPullRequests();
        verify(gitHubIssueClientService, times(2)).addPullRequestComment(eq(201L), contains("Step 6"));
        verify(gitHubIssueClientService).addIssueLabel(201L, "step6-terminal");

        service.pollManagedPullRequests();
        verify(gitHubIssueClientService, times(2)).addPullRequestComment(eq(201L), contains("Step 6"));
    }

    @Test
    void safeAutoFixCanExecuteCodeChangeViaStep5ExecutorPath() {
        Map<String, Object> pr = Map.of(
                "number", 202L,
                "title", "Issue #202: wrkbench typo",
                "headRefName", "codex/issue-202-safe-fix-test"
        );
        when(gitHubIssueClientService.listOpenPullRequests()).thenReturn(List.of(pr));
        when(gitHubIssueClientService.fetchIssueLabelNamesCaseInsensitive(202L)).thenReturn(Set.of());
        when(gitHubIssueClientService.getPullRequestReviews(202L))
                .thenReturn(List.of(Map.of("id", "r-202", "author", "reviewer", "body", "typo: wrkbench should be workbench", "url", "http://example")));
        when(gitHubIssueClientService.getPullRequestReviewComments(202L)).thenReturn(List.of());
        when(gitHubIssueClientService.getPullRequestIssueComments(202L)).thenReturn(List.of());
        when(step5IssueExecutionService.executeStep6SafeFix(eq(202L), eq("codex/issue-202-safe-fix-test"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(new Step5IssueExecutionService.Step6FixExecutionResult(
                        true,
                        "abcdef1234567890",
                        "codex/issue-202-safe-fix-test",
                        "",
                        ""
                ));

        service.pollManagedPullRequests();

        verify(step5IssueExecutionService).executeStep6SafeFix(eq(202L), eq("codex/issue-202-safe-fix-test"), org.mockito.ArgumentMatchers.anyMap());
        verify(gitHubIssueClientService, times(2)).addPullRequestComment(eq(202L), contains("Step 6"));
        verify(gitHubIssueClientService).addIssueLabel(202L, "step6-terminal");
    }
}
