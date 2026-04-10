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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class Step5IssueExecutionService {

    private static final Logger log = LoggerFactory.getLogger(Step5IssueExecutionService.class);

    private final String owner;
    private final String repo;
    private final String pythonCommand;
    private final String executorScriptPath;
    private final boolean autoMerge;
    private final long timeoutSeconds;
    private final FileAuditLogService fileAuditLogService;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Set<Long> inFlightIssues = ConcurrentHashMap.newKeySet();

    public Step5IssueExecutionService(@Value("${app.github.owner:}") String owner,
                                      @Value("${app.github.repo:}") String repo,
                                      @Value("${app.step5.executor.python:python3}") String pythonCommand,
                                      @Value("${app.step5.executor.script-path:../scripts/auto_issue_executor.py}") String executorScriptPath,
                                      @Value("${app.step5.executor.auto-merge:false}") boolean autoMerge,
                                      @Value("${app.step5.executor.timeout-seconds:180}") long timeoutSeconds,
                                      FileAuditLogService fileAuditLogService) {
        this.owner = owner;
        this.repo = repo;
        this.pythonCommand = pythonCommand;
        this.executorScriptPath = executorScriptPath;
        this.autoMerge = autoMerge;
        this.timeoutSeconds = timeoutSeconds;
        this.fileAuditLogService = fileAuditLogService;
    }

    public void executeIssueAsync(long issueNumber) {
        if (!inFlightIssues.add(issueNumber)) {
            log.info("Issue #{}: Step 5 execution already in progress; skipping duplicate trigger.", issueNumber);
            safeAudit("approved-issue-execution-skipped", issueNumber, Map.of("reason", "already-in-flight"), "");
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                executeIssue(issueNumber);
            } finally {
                inFlightIssues.remove(issueNumber);
            }
        }, executorService);
    }

    public void executeIssue(long issueNumber) {
        if (issueNumber <= 0) {
            return;
        }
        if (!StringUtils.hasText(owner) || !StringUtils.hasText(repo)) {
            log.warn("Skipping Step 5 execution for #{} because owner/repo are not configured.", issueNumber);
            safeAudit("approved-issue-execution-skipped", issueNumber, Map.of("reason", "missing-owner-repo"), "");
            return;
        }
        Path scriptPath = Path.of(executorScriptPath).normalize();
        if (!scriptPath.getFileName().toString().equals("auto_issue_executor.py") || !Files.exists(scriptPath)) {
            log.warn("Skipping Step 5 execution for #{} because script is unavailable at {}", issueNumber, scriptPath);
            safeAudit("approved-issue-execution-skipped", issueNumber, Map.of("reason", "missing-script"), "");
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
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Issue #{}: Step 5 execution error: {}", issueNumber, ex.getMessage());
            safeAudit("approved-issue-execution-failed", issueNumber, Map.of(), ex.getMessage());
        }
    }

    private void safeAudit(String operation, long issueNumber, Map<String, Object> metadata, String error) {
        try {
            fileAuditLogService.logStep5LifecycleEntry(operation, "issue-" + issueNumber, issueNumber, metadata, error);
        } catch (Exception ignored) {
            // Audit failures must never fail scheduler flow.
        }
    }
}
