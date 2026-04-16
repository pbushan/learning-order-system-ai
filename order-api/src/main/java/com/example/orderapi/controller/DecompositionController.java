package com.example.orderapi.controller;

import com.example.orderapi.dto.DecompositionRequest;
import com.example.orderapi.dto.DecompositionResponse;
import com.example.orderapi.dto.DecompositionStory;
import com.example.orderapi.dto.GitHubIssueCreateRequest;
import com.example.orderapi.dto.GitHubIssueCreateResponse;
import com.example.orderapi.service.DecompositionService;
import com.example.orderapi.service.FileAuditLogService;
import com.example.orderapi.service.GitHubIssueCreationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/intake")
public class DecompositionController {

    private final DecompositionService decompositionService;
    private final GitHubIssueCreationService gitHubIssueCreationService;
    private final FileAuditLogService fileAuditLogService;

    public DecompositionController(DecompositionService decompositionService,
                                   GitHubIssueCreationService gitHubIssueCreationService,
                                   FileAuditLogService fileAuditLogService) {
        this.decompositionService = decompositionService;
        this.gitHubIssueCreationService = gitHubIssueCreationService;
        this.fileAuditLogService = fileAuditLogService;
    }

    @PostMapping("/decompose")
    public ResponseEntity<DecompositionResponse> decompose(@RequestBody DecompositionRequest request) {
        try {
            return ResponseEntity.ok(decompositionService.decompose(request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(validationFallback(request));
        }
    }

    @PostMapping("/complete-to-github")
    public ResponseEntity<GitHubIssueCreateResponse> completeToGitHub(@RequestBody DecompositionRequest request) {
        try {
            DecompositionResponse decomposition = decompositionService.decompose(request);
            if (!decomposition.isDecompositionComplete()) {
                return failureWithAudit(
                        HttpStatus.BAD_GATEWAY,
                        "Decomposition did not complete successfully.",
                        request,
                        decomposition,
                        null
                );
            }
            if (decomposition.getStories() == null || decomposition.getStories().isEmpty()) {
                return failureWithAudit(
                        HttpStatus.BAD_GATEWAY,
                        "Decomposition did not produce stories.",
                        request,
                        decomposition,
                        null
                );
            }

            String sourceType = resolveSourceType(request);
            GitHubIssueCreateRequest issueRequest = new GitHubIssueCreateRequest();
            issueRequest.setRequestId(resolveRequestId(decomposition.getRequestId(), request));
            issueRequest.setSourceType(sourceType);
            issueRequest.setStories(decomposition.getStories());
            GitHubIssueCreateResponse issueResponse = gitHubIssueCreationService.createFromDecomposition(issueRequest);
            if (issueResponse == null || issueResponse.getIssues() == null) {
                return failureWithAudit(
                        HttpStatus.BAD_GATEWAY,
                        "GitHub issue creation returned an invalid response.",
                        request,
                        decomposition,
                        sourceType
                );
            }
            if (!issueResponse.isIssuesCreated() || issueResponse.getIssues().isEmpty()) {
                return failureWithAudit(
                        HttpStatus.BAD_GATEWAY,
                        "GitHub issue creation returned no issues.",
                        request,
                        decomposition,
                        sourceType
                );
            }
            return ResponseEntity.ok(issueResponse);
        } catch (IllegalArgumentException ex) {
            String requestId = resolveRequestId(null, request);
            String safeError = normalizeIntakeValidationError(ex.getMessage());
            return ResponseEntity.badRequest()
                    .header("X-Error-Message", safeError)
                    .body(githubFailureResponse(requestId, safeError));
        } catch (IllegalStateException ex) {
            String requestId = resolveRequestId(null, request);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", ex.getMessage())
                    .body(githubFailureResponse(requestId, ex.getMessage()));
        }
    }

    private DecompositionResponse validationFallback(DecompositionRequest request) {
        DecompositionResponse response = new DecompositionResponse();
        String requestId = request != null ? request.getRequestId() : null;
        response.setRequestId((requestId != null && !requestId.isBlank()) ? requestId.trim() : "unknown-request");
        response.setDecompositionComplete(false);
        response.setStories(Collections.emptyList());
        return response;
    }

    private String resolveSourceType(DecompositionRequest request) {
        String sourceType = request != null && request.getStructuredData() != null
                ? request.getStructuredData().getType()
                : null;
        if (!StringUtils.hasText(sourceType)) {
            throw new IllegalArgumentException("structuredData.type is required");
        }
        String normalized = sourceType.trim().toLowerCase();
        if (!"bug".equals(normalized) && !"feature".equals(normalized)) {
            throw new IllegalArgumentException("structuredData.type must be bug or feature");
        }
        return normalized;
    }

    private GitHubIssueCreateResponse githubFailureResponse(String requestId, String error) {
        GitHubIssueCreateResponse response = new GitHubIssueCreateResponse();
        response.setRequestId(requestId);
        response.setIssuesCreated(false);
        response.setIssues(Collections.emptyList());
        response.setError(error != null ? error : "");
        return response;
    }

    private String resolveRequestId(String preferredRequestId, DecompositionRequest request) {
        if (preferredRequestId != null && !preferredRequestId.isBlank()) {
            return preferredRequestId.trim();
        }
        String requestId = request != null ? request.getRequestId() : null;
        return (requestId != null && !requestId.isBlank()) ? requestId.trim() : "unknown-request";
    }

    private String normalizeIntakeValidationError(String errorMessage) {
        if (errorMessage == null) {
            return "";
        }
        if (errorMessage.contains("structuredData.title is required")
                || errorMessage.contains("structuredData.description is required")
                || errorMessage.contains("structuredData.type is required")) {
            return "Intake completed without actionable bug/feature details, so no GitHub issues were created.";
        }
        return errorMessage;
    }

    private ResponseEntity<GitHubIssueCreateResponse> failureWithAudit(HttpStatus status,
                                                                        String errorMessage,
                                                                        DecompositionRequest request,
                                                                        DecompositionResponse decomposition,
                                                                        String sourceType) {
        String requestId = resolveRequestId(decomposition != null ? decomposition.getRequestId() : null, request);
        safeGitHubCreationAudit(
                requestId,
                sourceType,
                decomposition != null ? decomposition.getStories() : Collections.emptyList(),
                errorMessage
        );
        return ResponseEntity.status(status)
                .header("X-Error-Message", errorMessage)
                .body(githubFailureResponse(requestId, errorMessage));
    }

    private void safeGitHubCreationAudit(String requestId,
                                         String sourceType,
                                         List<DecompositionStory> stories,
                                         String error) {
        try {
            fileAuditLogService.logGitHubIssueCreationEntry(
                    requestId,
                    sourceType,
                    stories != null ? stories : Collections.emptyList(),
                    Collections.emptyList(),
                    error
            );
        } catch (Exception ignored) {
            // Logging must never fail the response path.
        }
    }
}
