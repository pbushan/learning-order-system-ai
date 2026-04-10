package com.example.orderapi.service;

import com.example.orderapi.dto.ApprovedGitHubIssue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

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
        when(step5IssueExecutionService.getExecutionAvailability())
                .thenReturn(new Step5IssueExecutionService.ExecutionAvailability(false, "missing-repo-root", null, null));

        ApprovedGitHubIssue stuck = issue(42L, "Stuck", List.of("approved-for-dev", "ai-in-progress"));
        ApprovedGitHubIssue step5Owned = issue(43L, "Owned", List.of("approved-for-dev", "ai-in-progress", "ai-generated", "portfolio"));
        when(gitHubIssueClientService.discoverApprovedInProgressIssues()).thenReturn(List.of(stuck, step5Owned));

        scheduler.pickUpApprovedIssues();

        verify(gitHubIssueClientService).discoverApprovedInProgressIssues();
        verify(gitHubIssueClientService, never()).removeIssueLabel(42L, "ai-in-progress");
        verify(gitHubIssueClientService).removeIssueLabel(43L, "ai-in-progress");
        verify(gitHubIssueClientService, never()).discoverApprovedIssues();
        verify(step5IssueExecutionService, never()).executeIssueAsync(42L);
        verify(step5IssueExecutionService, never()).executeIssueAsync(43L);
    }

    @Test
    void picksAndExecutesApprovedIssueWhenRuntimeAvailable() {
        when(step5IssueExecutionService.getExecutionAvailability())
                .thenReturn(new Step5IssueExecutionService.ExecutionAvailability(true, "", Path.of("/tmp"), Path.of("/tmp/scripts/auto_issue_executor.py")));

        ApprovedGitHubIssue approved = issue(84L, "Ready", List.of("approved-for-dev"));
        when(gitHubIssueClientService.discoverApprovedIssues()).thenReturn(List.of(approved));

        scheduler.pickUpApprovedIssues();

        verify(gitHubIssueClientService).addIssueLabel(84L, "ai-in-progress");
        verify(step5IssueExecutionService).executeIssueAsync(84L);
    }

    private ApprovedGitHubIssue issue(long number, String title, List<String> labels) {
        ApprovedGitHubIssue issue = new ApprovedGitHubIssue();
        issue.setIssueNumber(number);
        issue.setTitle(title);
        issue.setLabels(labels);
        issue.setBody("body");
        return issue;
    }
}
