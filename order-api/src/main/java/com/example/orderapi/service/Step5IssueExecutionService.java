package com.example.orderapi.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class Step5IssueExecutionService {

    private static final Logger log = LoggerFactory.getLogger(Step5IssueExecutionService.class);

    private final String owner;
    private final String repo;
    private final String pythonCommand;
    private final String executorScriptPath;
    private final String repoRootOverride;
    private final boolean autoMerge;
    private final long timeoutSeconds;
    private final GitHubIssueClientService gitHubIssueClientService;
    private final FileAuditLogService fileAuditLogService;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Set<Long> inFlightIssues = ConcurrentHashMap.newKeySet();
    private final AtomicReference<String> pausedReason = new AtomicReference<>("");

    public Step5IssueExecutionService(@Value("${app.github.owner:}") String owner,
                                      @Value("${app.github.repo:}") String repo,
                                      @Value("${app.step5.executor.python:python3}") String pythonCommand,
                                      @Value("${app.step5.executor.script-path:../scripts/auto_issue_executor.py}") String executorScriptPath,
                                      @Value("${app.step5.repo-root:}") String repoRootOverride,
                                      @Value("${app.step5.executor.auto-merge:false}") boolean autoMerge,
                                      @Value("${app.step5.executor.timeout-seconds:180}") long timeoutSeconds,
                                      GitHubIssueClientService gitHubIssueClientService,
                                      FileAuditLogService fileAuditLogService) {
        this.owner = owner;
        this.repo = repo;
        this.pythonCommand = pythonCommand;
        this.executorScriptPath = executorScriptPath;
        this.repoRootOverride = repoRootOverride;
        this.autoMerge = autoMerge;
        this.timeoutSeconds = timeoutSeconds;
        this.gitHubIssueClientService = gitHubIssueClientService;
        this.fileAuditLogService = fileAuditLogService;
    }

    public record ExecutionAvailability(boolean available, String reason) {
    }

    public void setExecutionPaused(boolean paused, String reason) {
        if (!paused) {
            pausedReason.set("");
            return;
        }
        pausedReason.set(StringUtils.hasText(reason) ? reason : "execution-paused");
    }

    public ExecutionAvailability checkExecutionAvailability() {
        if (!StringUtils.hasText(owner) || !StringUtils.hasText(repo)) {
            return new ExecutionAvailability(false, "missing-owner-repo");
        }
        Path repoRoot = resolveCurrentRepoRoot();
        if (repoRoot == null || !Files.isDirectory(repoRoot)) {
            return new ExecutionAvailability(false, "missing-repo-root");
        }
        Path scriptPath = resolveScriptPath(repoRoot);
        if (scriptPath == null || !Files.exists(scriptPath)) {
            return new ExecutionAvailability(false, "missing-script");
        }
        return new ExecutionAvailability(true, "");
    }

    public void executeIssueAsync(long issueNumber) {
        String currentPauseReason = pausedReason.get();
        if (StringUtils.hasText(currentPauseReason)) {
            log.info("Issue #{}: Step 5 execution currently paused. reason={}", issueNumber, currentPauseReason);
            safeAudit("approved-issue-execution-skipped", issueNumber, Map.of("reason", "execution-paused:" + currentPauseReason), "");
            resetIssueForRetry(issueNumber, "execution-paused");
            return;
        }
        if (!inFlightIssues.add(issueNumber)) {
            log.info("Issue #{}: Step 5 execution already in progress; skipping duplicate trigger.", issueNumber);
            safeAudit("approved-issue-execution-skipped", issueNumber, Map.of("reason", "already-in-flight"), "");
            return;
        }
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    executeIssue(issueNumber);
                } finally {
                    inFlightIssues.remove(issueNumber);
                }
            }, executorService);
        } catch (RuntimeException ex) {
            inFlightIssues.remove(issueNumber);
            throw ex;
        }
    }

    public void executeIssue(long issueNumber) {
        if (issueNumber <= 0) {
            return;
        }
        ExecutionAvailability availability = checkExecutionAvailability();
        if (!availability.available()) {
            log.warn("Skipping Step 5 execution for #{} because {}.", issueNumber, availability.reason());
            safeAudit("approved-issue-execution-skipped", issueNumber, Map.of("reason", availability.reason()), "");
            resetIssueForRetry(issueNumber, availability.reason());
            return;
        }
        Path repoRoot = resolveCurrentRepoRoot();

        Path scriptPath = resolveScriptPath(repoRoot);
        if (scriptPath == null || !scriptPath.getFileName().toString().equals("auto_issue_executor.py")) {
            log.warn("Skipping Step 5 execution for #{} because script is unavailable under repo scripts directory.", issueNumber);
            safeAudit("approved-issue-execution-skipped", issueNumber, Map.of("reason", "missing-script"), "");
            resetIssueForRetry(issueNumber, "missing-script");
            return;
        }

        List<String> command = new ArrayList<>();
        command.add(pythonCommand);
        command.add(scriptPath.toString());
        command.add("--owner");
        command.add(owner.trim());
        command.add("--repo");
        command.add(repo.trim());
        command.add("--issue");
        command.add(String.valueOf(issueNumber));
        command.add("--once");
        command.add("--allow-in-progress");
        if (autoMerge) {
            command.add("--auto-merge");
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        processBuilder.directory(repoRoot.toFile());
        processBuilder.environment().put("ALLOW_AUTO_MERGE", String.valueOf(autoMerge));

        try {
            log.info("Issue #{}: starting Step 5 execution command (post-pickup).", issueNumber);
            safeAudit("approved-issue-execution-started", issueNumber, Map.of("script", "auto_issue_executor.py"), "");

            Process process = processBuilder.start();
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                log.warn("Issue #{}: Step 5 execution timed out after {} seconds.", issueNumber, timeoutSeconds);
                safeAudit("approved-issue-execution-failed", issueNumber, Map.of("reason", "timeout"), "execution-timeout");
                resetIssueForRetry(issueNumber, "execution-timeout");
                return;
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.exitValue();

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("exitCode", exitCode);
            metadata.put("output", output == null ? "" : output.trim());

            if (exitCode == 0) {
                log.info("Issue #{}: Step 5 execution finished. exitCode={}", issueNumber, exitCode);
                safeAudit("approved-issue-execution-finished", issueNumber, metadata, "");
            } else {
                log.warn("Issue #{}: Step 5 execution failed. exitCode={}, output={}", issueNumber, exitCode, output);
                safeAudit("approved-issue-execution-failed", issueNumber, metadata, "execution-command-failed");
                resetIssueForRetry(issueNumber, "execution-command-failed");
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Issue #{}: Step 5 execution error: {}", issueNumber, ex.getMessage());
            safeAudit("approved-issue-execution-failed", issueNumber, Map.of(), ex.getMessage());
            resetIssueForRetry(issueNumber, "execution-service-exception");
        }
    }

    private void safeAudit(String operation, long issueNumber, Map<String, Object> metadata, String error) {
        try {
            fileAuditLogService.logStep5LifecycleEntry(operation, "issue-" + issueNumber, issueNumber, metadata, error);
        } catch (Exception ignored) {
            // Audit failures must never fail scheduler flow.
        }
    }

    private void resetIssueForRetry(long issueNumber, String reason) {
        try {
            boolean removed = gitHubIssueClientService.removeIssueLabelCaseInsensitive(issueNumber, "ai-in-progress");
            if (removed) {
                log.info("Issue #{}: reset ai-in-progress label for retry. reason={}", issueNumber, reason);
                safeAudit("approved-issue-reset-for-retry", issueNumber, Map.of("reason", reason, "label", "ai-in-progress"), "");
            } else {
                log.info("Issue #{}: ai-in-progress label already absent. reason={}", issueNumber, reason);
                safeAudit("approved-issue-reset-skipped", issueNumber, Map.of("reason", "label-not-present", "label", "ai-in-progress"), "");
            }
        } catch (Exception ex) {
            String error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            log.warn("Issue #{}: failed to reset ai-in-progress label: {}", issueNumber, error);
            safeAudit("approved-issue-reset-failed",
                    issueNumber,
                    Map.of("reason", reason, "label", "ai-in-progress", "errorType", ex.getClass().getSimpleName()),
                    error);
        }
    }

    private Path resolveScriptPath(Path repoRoot) {
        if (!StringUtils.hasText(executorScriptPath)) {
            return null;
        }
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        Path scriptsDir = repoRoot.resolve("scripts").normalize();
        candidates.add(scriptsDir.resolve("auto_issue_executor.py").normalize());

        Path configured = Path.of(executorScriptPath);
        if (configured.isAbsolute()) {
            candidates.add(configured.normalize());
        } else {
            candidates.add(repoRoot.resolve(executorScriptPath).normalize());
        }

        for (Path candidate : candidates) {
            if (Files.exists(candidate) && "auto_issue_executor.py".equals(candidate.getFileName().toString())) {
                Path normalizedCandidate = candidate.normalize();
                if (!normalizedCandidate.startsWith(scriptsDir)) {
                    continue;
                }
                return candidate;
            }
        }
        return null;
    }

    private Path resolveRepoRoot(Path scriptPath) {
        Path current = scriptPath.getParent();
        while (current != null) {
            if (Files.exists(current.resolve(".git"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private Path resolveCurrentRepoRoot() {
        if (StringUtils.hasText(repoRootOverride)) {
            Path configured = Path.of(repoRootOverride).normalize();
            if (Files.exists(configured.resolve(".git"))) {
                return configured;
            }
        }
        return resolveRepoRoot(Path.of(System.getProperty("user.dir")).normalize());
    }
}
