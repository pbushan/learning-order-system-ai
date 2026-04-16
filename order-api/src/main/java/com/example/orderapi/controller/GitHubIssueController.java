package com.example.orderapi.controller;

import com.example.orderapi.dto.GitHubIssueCreateRequest;
import com.example.orderapi.dto.GitHubIssueCreateResponse;
import com.example.orderapi.service.GitHubIssueCreationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

@RestController
@RequestMapping("/api/github/issues")
public class GitHubIssueController {

    private final GitHubIssueCreationService gitHubIssueCreationService;

    public GitHubIssueController(GitHubIssueCreationService gitHubIssueCreationService) {
        this.gitHubIssueCreationService = gitHubIssueCreationService;
    }

    @PostMapping("/create-from-decomposition")
    public ResponseEntity<GitHubIssueCreateResponse> createFromDecomposition(@RequestBody GitHubIssueCreateRequest request) {
        try {
            return ResponseEntity.ok(gitHubIssueCreationService.createFromDecomposition(request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .header("X-Error-Message", ex.getMessage())
                    .body(failureResponse(request, ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", ex.getMessage())
                    .body(failureResponse(request, ex.getMessage()));
        }
    }

    private GitHubIssueCreateResponse failureResponse(GitHubIssueCreateRequest request, String error) {
        GitHubIssueCreateResponse response = new GitHubIssueCreateResponse();
        String requestId = request != null ? request.getRequestId() : null;
        response.setRequestId(StringUtils.hasText(requestId) ? requestId.trim() : "unknown-request");
        String traceId = request != null ? request.getTraceId() : null;
        response.setTraceId(StringUtils.hasText(traceId) ? traceId.trim() : "trace-" + response.getRequestId());
        response.setIssuesCreated(false);
        response.setIssues(Collections.emptyList());
        response.setError(error != null ? error : "");
        return response;
    }
}
