package com.example.orderapi.service;

import com.example.orderapi.dto.ApprovedGitHubIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ApprovedIssuePickupScheduler {

    private static final Logger log = LoggerFactory.getLogger(ApprovedIssuePickupScheduler.class);

    private final GitHubIssueClientService gitHubIssueClientService;
    private final Step5IssueExecutionService step5IssueExecutionService;
    private final FileAuditLogService fileAuditLogService;
    private final AtomicBoolean pollInProgress = new AtomicBoolean(false);
    private final Map<Long, Long> recentlyPickedAtMs = new ConcurrentHashMap<>();

    public ApprovedIssuePickupScheduler(GitHubIssueClientService gitHubIssueClientService,
                                        Step5IssueExecutionService step5IssueExecutionService,
                                        FileAuditLogService fileAuditLogService) {
        this.gitHubIssueClientService = gitHubIssueClientService;
        this.step5IssueExecutionService = step5IssueExecutionService;
        this.fileAuditLogService = fileAuditLogService;
    }

    @Scheduled(fixedDelayString = "${app.step5.poll-interval-ms:20000}", initialDelayString = "${app.step5.initial-delay-ms:5000}")
    public void pickUpApprovedIssues() {
        if (!pollInProgress.compareAndSet(false, true)) {
            log.info("Skipping Step 5 poll because a previous run is still in progress.");
            safeAudit("approved-issue-poll-skipped", null, Map.of("reason", "poll-already-running"), "");
            return;
        }

        Step5IssueExecutionService.ExecutionAvailability availability = step5IssueExecutionService.getExecutionAvailability();
        if (!availability.available()) {
            log.warn("Step 5 poll cannot execute issues because {}.", availability.reason());
            safeAudit("approved-issue-poll-skipped", null, Map.of("reason", availability.reason()), "");
            resetStuckInProgressIssues(availability.reason());
            pollInProgress.set(false);
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
                if (wasRecentlyPicked(issueNumber)) {
                    log.info("Skipping issue #{} because it was picked recently.", issueNumber);
                    safeAudit("approved-issue-skipped", issueNumber, Map.of("reason", "recently-picked"), "");
                    continue;
                }

                try {
                    gitHubIssueClientService.addIssueLabel(issueNumber, "ai-in-progress");
                    recentlyPickedAtMs.put(issueNumber, System.currentTimeMillis());
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("title", issue.getTitle());
                    metadata.put("labels", issue.getLabels() != null ? issue.getLabels() : List.of());
                    metadata.put("nextStep", "agent-execution");
                    log.info("Picked approved issue #{} and added ai-in-progress label.", issueNumber);
                    safeAudit("approved-issue-picked", issueNumber, metadata, "");
                    step5IssueExecutionService.executeIssueAsync(issueNumber);
                } catch (Exception ex) {
                    log.warn("Failed to mark issue #{} as ai-in-progress: {}", issueNumber, ex.getMessage());
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("title", issue.getTitle());
                    metadata.put("labels", issue.getLabels() != null ? issue.getLabels() : List.of());
                    safeAudit("approved-issue-pick-failed", issueNumber, metadata, ex.getMessage());
                }
            }
        } finally {
            pollInProgress.set(false);
        }
    }

    private void resetStuckInProgressIssues(String reason) {
        List<ApprovedGitHubIssue> inProgressIssues;
        try {
            inProgressIssues = gitHubIssueClientService.discoverApprovedInProgressIssues();
        } catch (Exception ex) {
            log.warn("Failed to discover in-progress approved issues for reset: {}", ex.getMessage());
            safeAudit("approved-issue-reset-for-retry-failed", null, Map.of("reason", reason), ex.getMessage());
            return;
        }

        for (ApprovedGitHubIssue issue : inProgressIssues) {
            if (issue == null || issue.getIssueNumber() <= 0) {
                continue;
            }
            long issueNumber = issue.getIssueNumber();
            try {
                gitHubIssueClientService.removeIssueLabel(issueNumber, "ai-in-progress");
                recentlyPickedAtMs.remove(issueNumber);
                log.info("Reset issue #{} for retry because Step 5 runtime is unavailable. reason={}", issueNumber, reason);
                safeAudit("approved-issue-reset-for-retry", issueNumber, Map.of("reason", reason), "");
            } catch (Exception ex) {
                log.warn("Failed resetting issue #{} for retry: {}", issueNumber, ex.getMessage());
                safeAudit("approved-issue-reset-for-retry-failed", issueNumber, Map.of("reason", reason), ex.getMessage());
            }
        }
    }

    private void safeAudit(String operation, Long issueNumber, Map<String, Object> metadata, String error) {
        try {
            String requestId = issueNumber != null ? "issue-" + issueNumber : "step5-poll";
            fileAuditLogService.logStep5LifecycleEntry(operation, requestId, issueNumber, metadata, error);
        } catch (Exception ignored) {
            // Audit failures must never fail scheduler flow.
        }
    }

    private boolean wasRecentlyPicked(long issueNumber) {
        long now = System.currentTimeMillis();
        long ttlMs = 120_000L;
        recentlyPickedAtMs.entrySet().removeIf(entry -> (now - entry.getValue()) > ttlMs);
        Long pickedAt = recentlyPickedAtMs.get(issueNumber);
        return pickedAt != null && (now - pickedAt) <= ttlMs;
    }
}
