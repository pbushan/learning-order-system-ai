package com.example.orderapi.service;

import com.example.orderapi.dto.ApprovedGitHubIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ApprovedIssuePickupScheduler {

    private static final Logger log = LoggerFactory.getLogger(ApprovedIssuePickupScheduler.class);

    private final GitHubIssueClientService gitHubIssueClientService;
    private final FileAuditLogService fileAuditLogService;
    private final AtomicBoolean pollInProgress = new AtomicBoolean(false);

    public ApprovedIssuePickupScheduler(GitHubIssueClientService gitHubIssueClientService,
                                        FileAuditLogService fileAuditLogService) {
        this.gitHubIssueClientService = gitHubIssueClientService;
        this.fileAuditLogService = fileAuditLogService;
    }

    @Scheduled(fixedDelayString = "${app.step5.poll-interval-ms:20000}", initialDelayString = "${app.step5.initial-delay-ms:5000}")
    public void pickUpApprovedIssues() {
        if (!pollInProgress.compareAndSet(false, true)) {
            log.info("Skipping Step 5 poll because a previous run is still in progress.");
            safeAudit("approved-issue-poll-skipped", null, Map.of("reason", "poll-already-running"), "");
            return;
        }

        List<ApprovedGitHubIssue> issues;
        try {
            issues = gitHubIssueClientService.discoverApprovedIssues();
        } catch (Exception ex) {
            log.warn("Step 5 poll failed while discovering approved issues: {}", ex.getMessage());
            safeAudit("approved-issue-poll-failed", null, Map.of(), ex.getMessage());
            pollInProgress.set(false);
            return;
        }

        try {
            log.info("Step 5 poll completed. approvedIssuesFound={}", issues.size());
            safeAudit("approved-issue-poll-ran", null, Map.of("approvedIssuesFound", issues.size()), "");

            for (ApprovedGitHubIssue issue : issues) {
                if (issue == null || issue.getIssueNumber() <= 0) {
                    log.info("Skipping invalid approved issue payload.");
                    safeAudit("approved-issue-skipped", null, Map.of("reason", "invalid-issue-payload"), "");
                    continue;
                }

                long issueNumber = issue.getIssueNumber();

                try {
                    gitHubIssueClientService.addIssueLabel(issueNumber, "ai-in-progress");
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("title", issue.getTitle());
                    metadata.put("labels", issue.getLabels() != null ? issue.getLabels() : List.of());
                    metadata.put("nextStep", "agent-execution");
                    log.info("Picked approved issue #{} and added ai-in-progress label.", issueNumber);
                    safeAudit("approved-issue-picked", issueNumber, metadata, "");
                } catch (Exception ex) {
                    log.warn("Failed to mark issue #{} as ai-in-progress: {}", issueNumber, ex.getMessage());
                    safeAudit("approved-issue-pick-failed", issueNumber, Map.of(), ex.getMessage());
                }
            }
        } finally {
            pollInProgress.set(false);
        }
    }

    private void safeAudit(String operation, Long issueNumber, Map<String, Object> metadata, String error) {
        try {
            String requestId = issueNumber != null ? "issue-" + issueNumber : "";
            fileAuditLogService.logStep5LifecycleEntry(operation, requestId, issueNumber, metadata, error);
        } catch (Exception ignored) {
            // Audit failures must never fail scheduler flow.
        }
    }
}
