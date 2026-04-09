package com.example.orderapi.controller;

import com.example.orderapi.dto.DecompositionRequest;
import com.example.orderapi.dto.DecompositionResponse;
import com.example.orderapi.dto.GitHubIssueCreateRequest;
import com.example.orderapi.dto.GitHubIssueCreateResponse;
import com.example.orderapi.service.DecompositionService;
import com.example.orderapi.service.GitHubIssueCreationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import java.util.Collections;

@RestController
@RequestMapping("/api/intake")
public class DecompositionController {

    private final DecompositionService decompositionService;
    private final GitHubIssueCreationService gitHubIssueCreationService;

    public DecompositionController(DecompositionService decompositionService,
                                   GitHubIssueCreationService gitHubIssueCreationService) {
        this.decompositionService = decompositionService;
        this.gitHubIssueCreationService = gitHubIssueCreationService;
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
            if (decomposition.getStories() == null || decomposition.getStories().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .header("X-Error-Message", "Decomposition did not produce stories.")
                        .body(githubFailureResponse(request));
            }

            GitHubIssueCreateRequest issueRequest = new GitHubIssueCreateRequest();
            issueRequest.setRequestId(decomposition.getRequestId());
            issueRequest.setSourceType(resolveSourceType(request));
            issueRequest.setStories(decomposition.getStories());
            GitHubIssueCreateResponse issueResponse = gitHubIssueCreationService.createFromDecomposition(issueRequest);
            if (issueResponse == null || issueResponse.getIssues() == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .header("X-Error-Message", "GitHub issue creation returned an invalid response.")
                        .body(githubFailureResponse(request));
            }
            return ResponseEntity.ok(issueResponse);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .header("X-Error-Message", ex.getMessage())
                    .body(githubFailureResponse(request));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", ex.getMessage())
                    .body(githubFailureResponse(request));
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

    private GitHubIssueCreateResponse githubFailureResponse(DecompositionRequest request) {
        GitHubIssueCreateResponse response = new GitHubIssueCreateResponse();
        String requestId = request != null ? request.getRequestId() : null;
        response.setRequestId((requestId != null && !requestId.isBlank()) ? requestId.trim() : "unknown-request");
        response.setIssuesCreated(false);
        response.setIssues(Collections.emptyList());
        return response;
    }
}
