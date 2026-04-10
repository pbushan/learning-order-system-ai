package com.example.orderapi.service;

import com.example.orderapi.dto.ApprovedGitHubIssue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovedIssuePickupSchedulerTest {

    @Mock
    private GitHubIssueClientService gitHubIssueClientService;

    @Mock
    private Step5IssueExecutionService step5IssueExecutionService;

    @Mock
    private FileAuditLogService fileAuditLogService;

    private ApprovedIssuePickupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ApprovedIssuePickupScheduler(gitHubIssueClientService, step5IssueExecutionService, fileAuditLogService);
    }

    @Test
    void resetsInProgressIssuesWhenExecutionUnavailable() {
        when(step5IssueExecutionService.checkExecutionAvailability())
                .thenReturn(new Step5IssueExecutionService.ExecutionAvailability(false, "missing-repo-root"));

        ApprovedGitHubIssue stuck = issue(42L, "Stuck", List.of("approved-for-dev", "ai-in-progress"));
        ApprovedGitHubIssue step5Owned = issue(43L, "Owned", List.of("approved-for-dev", "ai-in-progress", "ai-generated", "portfolio"));
        when(gitHubIssueClientService.discoverApprovedInProgressIssues()).thenReturn(List.of(stuck, step5Owned));

        scheduler.pickUpApprovedIssues();

        verify(gitHubIssueClientService).discoverApprovedInProgressIssues();
        verify(gitHubIssueClientService, never()).removeIssueLabel(42L, "ai-in-progress");
        verify(gitHubIssueClientService).removeIssueLabel(43L, "ai-in-progress");
        verify(step5IssueExecutionService, never()).executeIssueAsync(42L);
        verify(step5IssueExecutionService, never()).executeIssueAsync(43L);
    }

    @Test
    void resetsCaseVariantOwnedIssueUsingBodyMarkers() {
        when(step5IssueExecutionService.checkExecutionAvailability())
                .thenReturn(new Step5IssueExecutionService.ExecutionAvailability(false, "missing-repo-root"));

        ApprovedGitHubIssue ownedByBody = issue(
                55L,
                "Owned by body",
                List.of("Approved-For-Dev", "AI-IN-PROGRESS", "Portfolio"),
                "## Story ID\nabc\n\n## PR Safety\n- target: under-30000-char-patch"
        );
        when(gitHubIssueClientService.discoverApprovedInProgressIssues()).thenReturn(List.of(ownedByBody));

        scheduler.pickUpApprovedIssues();

        verify(gitHubIssueClientService).removeIssueLabel(55L, "ai-in-progress");
    }

    @Test
    void auditsResetSkippedWhenLabelAlreadyMissing() {
        when(step5IssueExecutionService.checkExecutionAvailability())
                .thenReturn(new Step5IssueExecutionService.ExecutionAvailability(false, "missing-repo-root"));

        ApprovedGitHubIssue step5Owned = issue(77L, "Owned", List.of("approved-for-dev", "ai-in-progress", "ai-generated", "portfolio"));
        when(gitHubIssueClientService.discoverApprovedInProgressIssues()).thenReturn(List.of(step5Owned));
        when(gitHubIssueClientService.removeIssueLabel(77L, "ai-in-progress")).thenReturn(false);

        scheduler.pickUpApprovedIssues();

        verify(fileAuditLogService).logStep5LifecycleEntry(
                eq("approved-issue-reset-skipped"),
                eq("issue-77"),
                eq(77L),
                any(),
                eq("")
        );
    }

    @Test
    void picksAndExecutesApprovedIssueWhenRuntimeAvailable() {
        when(step5IssueExecutionService.checkExecutionAvailability())
                .thenReturn(new Step5IssueExecutionService.ExecutionAvailability(true, ""));

        ApprovedGitHubIssue approved = issue(84L, "Ready", List.of("approved-for-dev"));
        when(gitHubIssueClientService.discoverApprovedIssues()).thenReturn(List.of(approved));

        scheduler.pickUpApprovedIssues();

        verify(gitHubIssueClientService).addIssueLabel(84L, "ai-in-progress");
        verify(step5IssueExecutionService).executeIssueAsync(84L);
    }

    private ApprovedGitHubIssue issue(long number, String title, List<String> labels) {
        return issue(number, title, labels, "body");
    }

    private ApprovedGitHubIssue issue(long number, String title, List<String> labels, String body) {
        ApprovedGitHubIssue issue = new ApprovedGitHubIssue();
        issue.setIssueNumber(number);
        issue.setTitle(title);
        issue.setLabels(labels);
        issue.setBody(body);
        return issue;
    }
}
