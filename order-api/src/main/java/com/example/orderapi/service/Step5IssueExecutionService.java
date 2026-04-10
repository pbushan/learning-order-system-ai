package com.example.orderapi.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class Step5IssueExecutionService {

    private static final Logger log = LoggerFactory.getLogger(Step5IssueExecutionService.class);

    private final String owner;
    private final String repo;
    private final String pythonCommand;
    private final String executorScriptPath;
    private final boolean autoMerge;
    private final FileAuditLogService fileAuditLogService;

    public Step5IssueExecutionService(@Value("${app.github.owner:}") String owner,
                                      @Value("${app.github.repo:}") String repo,
                                      @Value("${app.step5.executor.python:python3}") String pythonCommand,
                                      @Value("${app.step5.executor.script-path:../scripts/auto_issue_executor.py}") String executorScriptPath,
                                      @Value("${app.step5.executor.auto-merge:true}") boolean autoMerge,
                                      FileAuditLogService fileAuditLogService) {
        this.owner = owner;
        this.repo = repo;
        this.pythonCommand = pythonCommand;
        this.executorScriptPath = executorScriptPath;
        this.autoMerge = autoMerge;
        this.fileAuditLogService = fileAuditLogService;
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

        List<String> command = new ArrayList<>();
        command.add(pythonCommand);
        command.add(executorScriptPath);
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

        try {
            log.info("Issue #{}: starting Step 5 execution command: {}", issueNumber, String.join(" ", command));
            safeAudit("approved-issue-execution-started", issueNumber, Map.of("command", command), "");

            Process process = processBuilder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();

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
