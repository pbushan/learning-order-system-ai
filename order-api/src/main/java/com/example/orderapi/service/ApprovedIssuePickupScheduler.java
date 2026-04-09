package com.example.orderapi.service;

import com.example.orderapi.dto.ApprovedGitHubIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ApprovedIssuePickupScheduler {

    private static final Logger log = LoggerFactory.getLogger(ApprovedIssuePickupScheduler.class);

    private final GitHubIssueClientService gitHubIssueClientService;
    private final FileAuditLogService fileAuditLogService;
    private final Set<Long> activeIssueNumbers = ConcurrentHashMap.newKeySet();

    public ApprovedIssuePickupScheduler(GitHubIssueClientService gitHubIssueClientService,
                                        FileAuditLogService fileAuditLogService) {
        this.gitHubIssueClientService = gitHubIssueClientService;
        this.fileAuditLogService = fileAuditLogService;
    }

    @Scheduled(fixedDelayString = "${app.step5.poll-interval-ms:20000}", initialDelayString = "${app.step5.initial-delay-ms:5000}")
    public void pickUpApprovedIssues() {
        List<ApprovedGitHubIssue> issues;
        try {
            issues = gitHubIssueClientService.discoverApprovedIssues();
        } catch (Exception ex) {
            log.warn("Step 5 poll failed while discovering approved issues: {}", ex.getMessage());
            safeAudit("approved-issue-poll-failed", null, Map.of(), ex.getMessage());
            return;
        }

        log.info("Step 5 poll completed. approvedIssuesFound={}", issues.size());
        safeAudit("approved-issue-poll-ran", null, Map.of("approvedIssuesFound", issues.size()), "");

        for (ApprovedGitHubIssue issue : issues) {
            if (issue == null || issue.getIssueNumber() <= 0) {
                log.info("Skipping invalid approved issue payload.");
                safeAudit("approved-issue-skipped", null, Map.of("reason", "invalid-issue-payload"), "");
                continue;
            }

            long issueNumber = issue.getIssueNumber();
            if (!activeIssueNumbers.add(issueNumber)) {
                log.info("Skipping issue #{} because it is already active in this runtime.", issueNumber);
                safeAudit("approved-issue-skipped", issueNumber, Map.of("reason", "already-active-in-runtime"), "");
                continue;
            }

            try {
                gitHubIssueClientService.addIssueLabel(issueNumber, "ai-in-progress");
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("title", issue.getTitle());
                metadata.put("labels", issue.getLabels() != null ? issue.getLabels() : List.of());
                metadata.put("nextStep", "agent-execution");
                log.info("Picked approved issue #{} and added ai-in-progress label.", issueNumber);
                safeAudit("approved-issue-picked", issueNumber, metadata, "");
            } catch (Exception ex) {
                activeIssueNumbers.remove(issueNumber);
                log.warn("Failed to mark issue #{} as ai-in-progress: {}", issueNumber, ex.getMessage());
                safeAudit("approved-issue-pick-failed", issueNumber, Map.of(), ex.getMessage());
            }
        }
    }

    private void safeAudit(String operation, Long issueNumber, Map<String, Object> metadata, String error) {
        try {
            fileAuditLogService.logStep5LifecycleEntry(operation, "", issueNumber, metadata, error);
        } catch (Exception ignored) {
            // Audit failures must never fail scheduler flow.
        }
    }
}
