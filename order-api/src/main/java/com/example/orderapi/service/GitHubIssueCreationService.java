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

    public GitHubIssueCreationService(GitHubIssueClientService gitHubIssueClientService) {
        this.gitHubIssueClientService = gitHubIssueClientService;
    }

    public GitHubIssueCreateResponse createFromDecomposition(GitHubIssueCreateRequest request) {
        validateRequest(request);

        String requestId = request.getRequestId().trim();
        String sourceType = request.getSourceType().trim();
        List<GitHubIssueSummary> issues = new ArrayList<>();

        for (DecompositionStory story : request.getStories()) {
            GitHubIssueSummary issue = gitHubIssueClientService.createIssueForStory(sourceType, story);
            issue.setStoryId(story.getStoryId());
            issues.add(issue);
        }

        GitHubIssueCreateResponse response = new GitHubIssueCreateResponse();
        response.setRequestId(requestId);
        response.setIssuesCreated(!issues.isEmpty());
        response.setIssues(issues);
        return response;
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
}
