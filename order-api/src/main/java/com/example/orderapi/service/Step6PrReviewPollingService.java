package com.example.orderapi.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class Step6PrReviewPollingService {

    private static final Logger log = LoggerFactory.getLogger(Step6PrReviewPollingService.class);
    private static final String TERMINAL_LABEL = "step6-terminal";
    private static final String SELF_REVIEW_MARKER = "[step6-self-review]";
    private static final String TERMINAL_MARKER = "[step6-terminal]";

    private final boolean enabled;
    private final int maxCyclesPerPr;
    private final int maxSelfReviewAttempts;
    private final int waitCyclesBeforeSelfReview;
    private final GitHubIssueClientService gitHubIssueClientService;
    private final Step5IssueExecutionService step5IssueExecutionService;
    private final FileAuditLogService fileAuditLogService;
    private final AtomicBoolean pollInProgress = new AtomicBoolean(false);
    private final Map<Long, PrState> prStates = new ConcurrentHashMap<>();

    public Step6PrReviewPollingService(@Value("${app.step6.enabled:true}") boolean enabled,
                                       @Value("${app.step6.max-cycles-per-pr:3}") int maxCyclesPerPr,
                                       @Value("${app.step6.max-self-review-attempts:1}") int maxSelfReviewAttempts,
                                       @Value("${app.step6.wait-cycles-before-self-review:2}") int waitCyclesBeforeSelfReview,
                                       GitHubIssueClientService gitHubIssueClientService,
                                       Step5IssueExecutionService step5IssueExecutionService,
                                       FileAuditLogService fileAuditLogService) {
        this.enabled = enabled;
        this.maxCyclesPerPr = Math.max(1, maxCyclesPerPr);
        this.maxSelfReviewAttempts = Math.max(0, maxSelfReviewAttempts);
        this.waitCyclesBeforeSelfReview = Math.max(1, waitCyclesBeforeSelfReview);
        this.gitHubIssueClientService = gitHubIssueClientService;
        this.step5IssueExecutionService = step5IssueExecutionService;
        this.fileAuditLogService = fileAuditLogService;
    }

    @Scheduled(fixedDelayString = "${app.step6.poll-interval-ms:30000}", initialDelayString = "${app.step6.initial-delay-ms:10000}")
    public void pollManagedPullRequests() {
        if (!enabled) {
            return;
        }
        if (!pollInProgress.compareAndSet(false, true)) {
            log.info("Skipping Step 6 poll because a previous run is still in progress.");
            safeAudit("step6-pr-poll-skipped", null, Map.of("reason", "poll-already-running"), "");
            return;
        }

        try {
            List<Map<String, Object>> pulls = gitHubIssueClientService.listOpenPullRequests();
            List<Map<String, Object>> managed = new ArrayList<>();
            for (Map<String, Object> pull : pulls) {
                if (isManagedPullRequest(pull)) {
                    managed.add(pull);
                }
            }
            log.info("Step 6 poll completed. openManagedPrs={}", managed.size());
            safeAudit("step6-pr-poll-ran", null, Map.of("openManagedPrs", managed.size()), "");
            for (Map<String, Object> pull : managed) {
                handlePullRequest(pull);
            }
        } catch (Exception ex) {
            log.warn("Step 6 poll failed: {}", ex.getMessage());
            safeAudit("step6-pr-poll-failed", null, Map.of(), ex.getMessage());
        } finally {
            pollInProgress.set(false);
        }
    }

    private void handlePullRequest(Map<String, Object> pull) {
        long prNumber = toLong(pull.get("number"));
        if (prNumber <= 0) {
            return;
        }

        PrState state = prStates.computeIfAbsent(prNumber, ignored -> new PrState());
        if (state.terminalPosted) {
            return;
        }

        Set<String> labels = gitHubIssueClientService.fetchIssueLabelNamesCaseInsensitive(prNumber);
        if (labels.contains(TERMINAL_LABEL)) {
            state.terminalPosted = true;
            return;
        }

        if (state.cycleCount >= maxCyclesPerPr) {
            finalizeWithTerminalComment(prNumber, state, "STOPPED_MAX_CYCLES",
                    "Step 6 reached the configured max review cycles without new safe changes.");
            return;
        }

        ReviewPacket packet = collectReviewPacket(prNumber, pull, state);
        log.info("Step 6 PR #{} cycle={} newExternalSignals={} totalSignals={}",
                prNumber,
                state.cycleCount + 1,
                packet.newExternalItems().size(),
                packet.metadata().getOrDefault("totalSignals", 0));
        safeAudit("step6-review-comments-retrieved", prNumber, packet.metadata(), "");

        if (!packet.newExternalItems().isEmpty()) {
            state.noFeedbackCycles = 0;
            processFindings(prNumber, pull, state, classify(packet.newExternalItems(), false));
            return;
        }

        state.noFeedbackCycles++;
        if (state.noFeedbackCycles < waitCyclesBeforeSelfReview) {
            return;
        }

        if (state.selfReviewAttempts >= maxSelfReviewAttempts) {
            finalizeWithTerminalComment(prNumber, state, "DONE_NO_ACTION",
                    "No new external review feedback was found and self-review attempts are exhausted.");
            return;
        }

        List<ReviewSignal> selfSignals = buildSelfReviewSignals(prNumber, pull);
        state.selfReviewAttempts++;
        log.info("Step 6 PR #{} posted self-review fallback. findings={}", prNumber, selfSignals.size());
        safeAudit("step6-self-review-posted",
                prNumber,
                Map.of("selfReviewAttempt", state.selfReviewAttempts, "findingCount", selfSignals.size()),
                "");

        if (selfSignals.isEmpty()) {
            finalizeWithTerminalComment(prNumber, state, "DONE_NO_ACTION",
                    "Self-review found no conservative safe follow-up actions.");
            return;
        }

        processFindings(prNumber, pull, state, classify(selfSignals, true));
    }

    private ReviewPacket collectReviewPacket(long prNumber, Map<String, Object> pull, PrState state) {
        List<ReviewSignal> allSignals = new ArrayList<>();
        allSignals.addAll(toReviewSignals(gitHubIssueClientService.getPullRequestReviews(prNumber), "review-summary"));
        allSignals.addAll(toReviewSignals(gitHubIssueClientService.getPullRequestReviewComments(prNumber), "review-comment"));
        allSignals.addAll(toReviewSignals(gitHubIssueClientService.getPullRequestIssueComments(prNumber), "issue-comment"));

        List<ReviewSignal> newExternal = new ArrayList<>();
        for (ReviewSignal signal : allSignals) {
            if (signal == null || !StringUtils.hasText(signal.id)) {
                continue;
            }
            if (state.processedItemIds.contains(signal.id)) {
                continue;
            }
            if (isInternalStep6Comment(signal)) {
                continue;
            }
            newExternal.add(signal);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("cycle", state.cycleCount + 1);
        metadata.put("prNumber", prNumber);
        metadata.put("headRefName", safeText(pull.get("headRefName")));
        metadata.put("totalSignals", allSignals.size());
        metadata.put("newExternalSignals", newExternal.size());
        return new ReviewPacket(newExternal, metadata);
    }

    private List<ReviewSignal> toReviewSignals(List<Map<String, Object>> rows, String type) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<ReviewSignal> signals = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            String id = type + ":" + safeText(row.get("id"));
            signals.add(new ReviewSignal(
                    id,
                    type,
                    safeText(row.get("author")),
                    safeText(row.get("body")),
                    safeText(row.get("url"))
            ));
        }
        return signals;
    }

    private List<Finding> classify(List<ReviewSignal> signals, boolean selfReview) {
        if (signals == null || signals.isEmpty()) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        for (ReviewSignal signal : signals) {
            if (signal == null) {
                continue;
            }
            String bodyLower = signal.body.toLowerCase(Locale.ROOT);
            Classification classification;
            String reason;
            if (containsAny(bodyLower, "security", "secret", "injection", "auth bypass", "data corruption", "data loss",
                    "runtime crash", "nullpointer", "regression", "happy path", "contract break", "breaking")) {
                classification = Classification.NEEDS_HUMAN;
                reason = "Potential blocking risk under portfolio criteria.";
            } else if (containsAny(bodyLower, "typo", "spelling", "wording", "rename label", "copy fix", "wrkbench")) {
                classification = Classification.SAFE_AUTO_FIX;
                reason = "Small textual consistency fix.";
            } else {
                classification = Classification.ACKNOWLEDGE_ONLY;
                reason = "No blocking criterion matched; acknowledge and defer hardening.";
            }
            findings.add(new Finding(signal.id, classification, signal.type, signal.author, signal.body, signal.url, reason, selfReview));
        }
        return findings;
    }

    private void processFindings(long prNumber, Map<String, Object> pull, PrState state, List<Finding> findings) {
        if (findings == null || findings.isEmpty()) {
            finalizeWithTerminalComment(prNumber, state, "DONE_NO_ACTION", "No actionable findings were identified.");
            return;
        }

        boolean repeatDetected = false;
        int safeAutoFixCount = 0;
        int safeAutoFixAttemptCount = 0;
        int acknowledgeOnlyCount = 0;
        int needsHumanCount = 0;
        List<String> repeatedFindingIds = new ArrayList<>();
        List<String> safeActions = new ArrayList<>();

        for (Finding finding : findings) {
            if (finding == null) {
                continue;
            }
            String hash = hashFinding(finding);
            if (state.processedFindingHashes.contains(hash)) {
                repeatDetected = true;
                repeatedFindingIds.add(finding.sourceId);
                continue;
            }
            state.processedFindingHashes.add(hash);
            state.processedItemIds.add(finding.sourceId);

            if (finding.classification == Classification.SAFE_AUTO_FIX) {
                safeAutoFixAttemptCount++;
                String action = maybeApplySafePullRequestTextFix(prNumber, pull, finding);
                if (StringUtils.hasText(action)) {
                    safeAutoFixCount++;
                    safeActions.add(action);
                } else {
                    acknowledgeOnlyCount++;
                }
            } else if (finding.classification == Classification.NEEDS_HUMAN) {
                needsHumanCount++;
            } else {
                acknowledgeOnlyCount++;
            }
        }

        state.cycleCount++;
        state.noFeedbackCycles = 0;
        log.info("Step 6 PR #{} reconciliation summary: safeAutoFix={}, acknowledgeOnly={}, needsHuman={}, repeatDetected={}",
                prNumber, safeAutoFixCount, acknowledgeOnlyCount, needsHumanCount, repeatDetected);

        postReconciliationComment(
                prNumber,
                state,
                safeAutoFixCount,
                safeAutoFixAttemptCount,
                acknowledgeOnlyCount,
                needsHumanCount,
                safeActions,
                repeatDetected,
                repeatedFindingIds
        );

        if (repeatDetected) {
            finalizeWithTerminalComment(prNumber, state, "DONE_NEEDS_HUMAN",
                    "Repeated findings were detected across cycles. Further changes require human review.");
            return;
        }
        if (needsHumanCount > 0) {
            finalizeWithTerminalComment(prNumber, state, "DONE_NEEDS_HUMAN",
                    "Non-trivial findings remain and require human review judgment.");
            return;
        }
        if (safeAutoFixCount == 0 && acknowledgeOnlyCount == 0) {
            finalizeWithTerminalComment(prNumber, state, "DONE_NO_ACTION", "No-op cycle detected; stopping polling.");
            return;
        }
        finalizeWithTerminalComment(prNumber, state, "DONE_FIXED",
                "Safe reconciliation is complete for this PR cycle.");
    }

    private List<ReviewSignal> buildSelfReviewSignals(long prNumber, Map<String, Object> pull) {
        StringBuilder body = new StringBuilder();
        body.append(SELF_REVIEW_MARKER).append("\n");
        body.append("Step 6 self-review summary\n\n");
        body.append("- Scope: lightweight, conservative check for simple textual consistency\n");
        body.append("- Human review is still required before merge\n\n");

        String title = safeText(pull.get("title"));
        String lowerTitle = title.toLowerCase(Locale.ROOT);
        List<ReviewSignal> signals = new ArrayList<>();
        if (lowerTitle.contains("wrkbench")) {
            body.append("Findings:\n");
            body.append("- Typo detected in PR title (`wrkbench`). Safe text normalization can be attempted.\n");
            signals.add(new ReviewSignal(
                    "self-review:" + prNumber + ":" + System.currentTimeMillis(),
                    "self-review",
                    "step6-self-review",
                    "Typo in PR title: wrkbench should be workbench.",
                    ""
            ));
        } else {
            body.append("Findings:\n");
            body.append("- No conservative safe textual fix identified.\n");
        }

        gitHubIssueClientService.addPullRequestComment(prNumber, body.toString());
        return signals;
    }

    private String maybeApplySafePullRequestTextFix(long prNumber, Map<String, Object> pull, Finding finding) {
        if (finding == null || finding.classification != Classification.SAFE_AUTO_FIX) {
            return "";
        }

        String branch = safeText(pull.get("headRefName"));
        if (!StringUtils.hasText(branch)) {
            return "";
        }

        Map<String, Object> fixPacket = buildSafeFixPacket(prNumber, branch, finding);
        if (fixPacket.isEmpty()) {
            return "";
        }

        Step5IssueExecutionService.Step6FixExecutionResult executionResult =
                step5IssueExecutionService.executeStep6SafeFix(prNumber, branch, fixPacket);
        if (!executionResult.changed()) {
            safeAudit(
                    "step6-safe-fix-skipped",
                    prNumber,
                    Map.of("branch", branch, "reason", safeText(executionResult.error())),
                    safeText(executionResult.error())
            );
            return "";
        }
        safeAudit(
                "step6-safe-fix-applied",
                prNumber,
                Map.of("branch", branch, "commitSha", safeText(executionResult.commitSha()), "action", safeText(fixPacket.get("action"))),
                ""
        );
        return "Code changes pushed on `" + branch + "` (commit " + shortSha(executionResult.commitSha()) + ") for finding `" + finding.sourceId + "`.";
    }

    private Map<String, Object> buildSafeFixPacket(long prNumber, String branch, Finding finding) {
        String text = finding.text.toLowerCase(Locale.ROOT);
        if (!text.contains("wrkbench")) {
            return Map.of();
        }
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("prNumber", prNumber);
        packet.put("branch", branch);
        packet.put("findingId", finding.sourceId);
        packet.put("classification", finding.classification.name());
        packet.put("findingText", finding.text);
        packet.put("action", "replace-text");
        packet.put("fromText", "wrkbench");
        packet.put("toText", "workbench");
        packet.put("files", List.of("order-ui/index.html", "order-ui/app.js", "README.md"));
        return packet;
    }

    private void postReconciliationComment(long prNumber,
                                           PrState state,
                                           int safeAutoFixCount,
                                           int safeAutoFixAttemptCount,
                                           int acknowledgeOnlyCount,
                                           int needsHumanCount,
                                           List<String> safeActions,
                                           boolean repeatDetected,
                                           List<String> repeatedFindingIds) {
        StringBuilder message = new StringBuilder();
        message.append("Step 6 reconciliation cycle ").append(state.cycleCount).append("\n\n");
        message.append("- Safe auto-fix findings handled with code changes: ").append(safeAutoFixCount).append("\n");
        message.append("- Safe auto-fix findings attempted: ").append(safeAutoFixAttemptCount).append("\n");
        message.append("- Acknowledge-only findings: ").append(acknowledgeOnlyCount).append("\n");
        message.append("- Needs-human findings: ").append(needsHumanCount).append("\n");
        if (!safeActions.isEmpty()) {
            message.append("- Safe actions applied:\n");
            for (String action : safeActions) {
                message.append("  - ").append(action).append("\n");
            }
        }
        if (repeatDetected) {
            message.append("- Repeat finding detection: triggered for ").append(repeatedFindingIds).append("\n");
        }
        message.append("\nHuman review remains the merge gate for this portfolio workflow.");
        gitHubIssueClientService.addPullRequestComment(prNumber, message.toString());
        safeAudit("step6-reconciliation-posted",
                prNumber,
                Map.of(
                        "cycle", state.cycleCount,
                        "safeAutoFixCount", safeAutoFixCount,
                        "safeAutoFixAttemptCount", safeAutoFixAttemptCount,
                        "acknowledgeOnlyCount", acknowledgeOnlyCount,
                        "needsHumanCount", needsHumanCount,
                        "repeatDetected", repeatDetected
                ),
                "");
    }

    private void finalizeWithTerminalComment(long prNumber, PrState state, String terminalState, String reason) {
        if (state.terminalPosted) {
            return;
        }
        String message = TERMINAL_MARKER + "\n"
                + "Step 6 polling has stopped for this PR.\n\n"
                + "- State: " + terminalState + "\n"
                + "- Cycle count: " + state.cycleCount + "\n"
                + "- Self-review attempts: " + state.selfReviewAttempts + "\n"
                + "- Reason: " + safeText(reason) + "\n"
                + "- Safe automated reconciliation is complete for this bounded workflow.\n"
                + "- Any remaining non-trivial concerns require human review.\n"
                + "- PR is ready for human merge review when appropriate.";
        gitHubIssueClientService.addPullRequestComment(prNumber, message);
        gitHubIssueClientService.addIssueLabel(prNumber, TERMINAL_LABEL);
        log.info("Step 6 PR #{} terminal handoff posted. state={}", prNumber, terminalState);
        state.terminalPosted = true;
        safeAudit("step6-terminal-handoff-posted",
                prNumber,
                Map.of("state", terminalState, "reason", safeText(reason), "terminalAt", OffsetDateTime.now(ZoneOffset.UTC).toString()),
                "");
    }

    private boolean isManagedPullRequest(Map<String, Object> pull) {
        String headRefName = safeText(pull.get("headRefName")).toLowerCase(Locale.ROOT);
        String title = safeText(pull.get("title")).toLowerCase(Locale.ROOT);
        return headRefName.startsWith("codex/issue-") || title.startsWith("issue #");
    }

    private boolean isInternalStep6Comment(ReviewSignal signal) {
        if (signal == null) {
            return false;
        }
        String body = signal.body.toLowerCase(Locale.ROOT);
        return body.contains(SELF_REVIEW_MARKER) || body.contains(TERMINAL_MARKER);
    }

    private boolean containsAny(String text, String... keys) {
        if (!StringUtils.hasText(text) || keys == null) {
            return false;
        }
        for (String key : keys) {
            if (StringUtils.hasText(key) && text.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private String hashFinding(Finding finding) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = finding.classification + "|" + finding.sourceType + "|" + finding.text.toLowerCase(Locale.ROOT).trim();
            byte[] raw = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder();
            for (byte b : raw) {
                hash.append(String.format("%02x", b));
            }
            return hash.toString();
        } catch (Exception ex) {
            return String.valueOf(payloadHashFallback(finding));
        }
    }

    private int payloadHashFallback(Finding finding) {
        return (finding.classification + "|" + finding.sourceType + "|" + finding.text).hashCode();
    }

    private void safeAudit(String operation, Long prNumber, Map<String, Object> metadata, String error) {
        try {
            fileAuditLogService.logStep5LifecycleEntry(
                    operation,
                    prNumber != null ? "pr-" + prNumber : "step6-poll",
                    prNumber,
                    metadata != null ? metadata : Map.of(),
                    error
            );
        } catch (Exception ignored) {
            // Audit failures must never fail scheduler flow.
        }
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private String safeText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String shortSha(String sha) {
        if (!StringUtils.hasText(sha)) {
            return "unknown";
        }
        String trimmed = sha.trim();
        return trimmed.length() <= 8 ? trimmed : trimmed.substring(0, 8);
    }

    enum Classification {
        SAFE_AUTO_FIX,
        ACKNOWLEDGE_ONLY,
        NEEDS_HUMAN
    }

    record ReviewSignal(String id, String type, String author, String body, String url) {
    }

    record Finding(String sourceId,
                   Classification classification,
                   String sourceType,
                   String author,
                   String text,
                   String url,
                   String reason,
                   boolean selfReview) {
    }

    record ReviewPacket(List<ReviewSignal> newExternalItems, Map<String, Object> metadata) {
    }

    static class PrState {
        int cycleCount = 0;
        int selfReviewAttempts = 0;
        int noFeedbackCycles = 0;
        boolean terminalPosted = false;
        Set<String> processedItemIds = new LinkedHashSet<>();
        Set<String> processedFindingHashes = new LinkedHashSet<>();
    }
}
