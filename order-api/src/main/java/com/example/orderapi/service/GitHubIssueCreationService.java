package com.example.orderapi.service;

import com.example.orderapi.dto.DecompositionStory;
import com.example.orderapi.dto.GitHubIssueCreateRequest;
import com.example.orderapi.dto.GitHubIssueCreateResponse;
import com.example.orderapi.dto.GitHubIssueSummary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class GitHubIssueCreationService {

    private final GitHubIssueClientService gitHubIssueClientService;
    private final FileAuditLogService fileAuditLogService;

    public GitHubIssueCreationService(GitHubIssueClientService gitHubIssueClientService,
                                      FileAuditLogService fileAuditLogService) {
        this.gitHubIssueClientService = gitHubIssueClientService;
        this.fileAuditLogService = fileAuditLogService;
    }

    public GitHubIssueCreateResponse createFromDecomposition(GitHubIssueCreateRequest request) {
        validateRequest(request);

        String requestId = request.getRequestId().trim();
        String sourceType = request.getSourceType().trim();
        List<DecompositionStory> stories = request.getStories();
        List<GitHubIssueSummary> issues = new ArrayList<>();

        try {
            for (DecompositionStory story : stories) {
                GitHubIssueSummary issue = gitHubIssueClientService.createIssueForStory(sourceType, story);
                issue.setStoryId(story.getStoryId());
                issues.add(issue);
            }
            GitHubIssueCreateResponse response = new GitHubIssueCreateResponse();
            response.setRequestId(requestId);
            response.setIssuesCreated(!issues.isEmpty());
            response.setIssues(issues);

            safeAuditLog(requestId, sourceType, stories, issues, null);
            return response;
        } catch (Exception ex) {
            safeAuditLog(requestId, sourceType, stories, issues, ex.getMessage());
            throw ex;
        }
    }

    private void validateRequest(GitHubIssueCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.getRequestId())) {
            throw new IllegalArgumentException("requestId is required");
        }
        if (!StringUtils.hasText(request.getSourceType())) {
            throw new IllegalArgumentException("sourceType is required");
        }
        List<DecompositionStory> stories = request.getStories();
        if (stories == null || stories.isEmpty()) {
            throw new IllegalArgumentException("stories is required");
        }

        for (int i = 0; i < stories.size(); i++) {
            DecompositionStory story = stories.get(i);
            if (story == null || !StringUtils.hasText(story.getTitle())) {
                throw new IllegalArgumentException("stories[" + i + "].title is required");
            }
            if (!StringUtils.hasText(story.getDescription())) {
                throw new IllegalArgumentException("stories[" + i + "].description is required");
            }
        }
    }

    private void safeAuditLog(String requestId,
                              String sourceType,
                              List<DecompositionStory> stories,
                              List<GitHubIssueSummary> issues,
                              String error) {
        try {
            if (fileAuditLogService == null) {
                return;
            }
            fileAuditLogService.logGitHubIssueCreationEntry(requestId, sourceType, stories, issues, error);
        } catch (Exception ignored) {
            // Do not fail issue creation due to audit logging.
        }
    }
}
