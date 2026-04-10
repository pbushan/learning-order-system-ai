package com.example.orderapi.service;

import com.example.orderapi.dto.ApprovedGitHubIssue;
import com.example.orderapi.dto.DecompositionStory;
import com.example.orderapi.dto.GitHubIssueSummary;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GitHubIssueClientService {

    private final String token;
    private final String owner;
    private final String repo;
    private final FileAuditLogService fileAuditLogService;
    private final RestClient restClient;

    public GitHubIssueClientService(@Value("${app.github.token:}") String token,
                                    @Value("${app.github.owner:}") String owner,
                                    @Value("${app.github.repo:}") String repo,
                                    FileAuditLogService fileAuditLogService) {
        this.token = token;
        this.owner = owner;
        this.repo = repo;
        this.fileAuditLogService = fileAuditLogService;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(15000);

        this.restClient = RestClient.builder()
                .baseUrl("https://api.github.com")
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public GitHubIssueSummary createIssueForStory(String sourceType, DecompositionStory story) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("GitHub token is not configured. Set app.github.token or GITHUB_TOKEN.");
        }
        if (!StringUtils.hasText(owner) || !StringUtils.hasText(repo)) {
            throw new IllegalStateException("GitHub repository is not configured. Set app.github.owner and app.github.repo.");
        }
        if (story == null || !StringUtils.hasText(story.getTitle())) {
            throw new IllegalArgumentException("story.title is required");
        }
        if (!StringUtils.hasText(sourceType)) {
            throw new IllegalArgumentException("sourceType is required");
        }

        String normalizedSourceType = sourceType.trim().toLowerCase();
        List<String> labels = List.of(
                "ai-generated",
                "needs-human-approval",
                normalizedSourceType,
                "portfolio"
        );

        CreateIssueRequest request = new CreateIssueRequest();
        request.setTitle(story.getTitle().trim());
        request.setBody(buildIssueBody(story));
        request.setLabels(labels);

        GitHubIssueResponse response = restClient.post()
                .uri("/repos/{owner}/{repo}/issues", owner.trim(), repo.trim())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim())
                .body(request)
                .retrieve()
                .body(GitHubIssueResponse.class);

        if (response == null) {
            throw new IllegalStateException("GitHub issue creation failed: empty response.");
        }

        GitHubIssueSummary summary = new GitHubIssueSummary();
        summary.setIssueNumber(response.getNumber());
        summary.setIssueUrl(response.getHtmlUrl());
        summary.setTitle(response.getTitle());
        summary.setLabels(extractLabelNames(response.getLabels()));
        return summary;
    }

    public void addIssueLabel(long issueNumber, String label) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("GitHub token is not configured. Set app.github.token or GITHUB_TOKEN.");
        }
        if (!StringUtils.hasText(owner) || !StringUtils.hasText(repo)) {
            throw new IllegalStateException("GitHub repository is not configured. Set app.github.owner and app.github.repo.");
        }
        if (issueNumber <= 0) {
            throw new IllegalArgumentException("issueNumber must be > 0");
        }
        if (!StringUtils.hasText(label)) {
            throw new IllegalArgumentException("label is required");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("labels", List.of(label.trim()));

        try {
            restClient.post()
                    .uri("/repos/{owner}/{repo}/issues/{issueNumber}/labels", owner.trim(), repo.trim(), issueNumber)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            String responseBody = ex.getResponseBodyAsString();
            if (ex.getStatusCode().value() == 422
                    && responseBody != null
                    && (responseBody.contains("already_exists") || responseBody.toLowerCase().contains("already exists"))) {
                // Treat label-already-present responses as idempotent success for pickup.
                return;
            }
            throw ex;
        }
    }

    public void removeIssueLabel(long issueNumber, String label) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("GitHub token is not configured. Set app.github.token or GITHUB_TOKEN.");
        }
        if (!StringUtils.hasText(owner) || !StringUtils.hasText(repo)) {
            throw new IllegalStateException("GitHub repository is not configured. Set app.github.owner and app.github.repo.");
        }
        if (issueNumber <= 0) {
            throw new IllegalArgumentException("issueNumber must be > 0");
        }
        if (!StringUtils.hasText(label)) {
            throw new IllegalArgumentException("label is required");
        }

        String encodedLabel = UriUtils.encodePathSegment(label.trim(), StandardCharsets.UTF_8);
        int attempts = 0;
        while (attempts < 2) {
            attempts++;
            try {
                restClient.delete()
                        .uri("/repos/{owner}/{repo}/issues/{issueNumber}/labels/{label}",
                                owner.trim(), repo.trim(), issueNumber, encodedLabel)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim())
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                if (status == 404) {
                    return;
                }
                boolean transientFailure = status == 429 || (status >= 500 && status <= 599);
                if (!transientFailure || attempts >= 2) {
                    throw ex;
                }
                try {
                    Thread.sleep(300);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
    }

    public List<ApprovedGitHubIssue> discoverApprovedIssues() {
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("GitHub token is not configured. Set app.github.token or GITHUB_TOKEN.");
        }
        if (!StringUtils.hasText(owner) || !StringUtils.hasText(repo)) {
            throw new IllegalStateException("GitHub repository is not configured. Set app.github.owner and app.github.repo.");
        }

        List<ApprovedGitHubIssue> issues = new ArrayList<>();
        String error = null;

        try {
            GitHubIssueListItem[] response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{owner}/{repo}/issues")
                            .queryParam("state", "open")
                            .queryParam("labels", "approved-for-dev")
                            .queryParam("per_page", "100")
                            .build(owner.trim(), repo.trim()))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim())
                    .retrieve()
                    .body(GitHubIssueListItem[].class);

            if (response == null || response.length == 0) {
                return List.of();
            }

            for (GitHubIssueListItem item : response) {
                if (item == null || item.getPullRequest() != null) {
                    continue;
                }
                List<String> labels = extractLabelNames(item.getLabels());
                if (labels.contains("ai-in-progress")) {
                    continue;
                }

                ApprovedGitHubIssue normalized = new ApprovedGitHubIssue();
                normalized.setIssueNumber(item.getNumber());
                normalized.setTitle(item.getTitle());
                normalized.setBody(item.getBody());
                normalized.setLabels(labels);
                issues.add(normalized);
            }
            return issues;
        } catch (RuntimeException ex) {
            error = ex.getMessage();
            throw ex;
        } finally {
            safeAuditApprovedIssueSelection(issues, error);
        }
    }

    public List<ApprovedGitHubIssue> discoverApprovedInProgressIssues() {
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("GitHub token is not configured. Set app.github.token or GITHUB_TOKEN.");
        }
        if (!StringUtils.hasText(owner) || !StringUtils.hasText(repo)) {
            throw new IllegalStateException("GitHub repository is not configured. Set app.github.owner and app.github.repo.");
        }

        GitHubIssueListItem[] response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/issues")
                        .queryParam("state", "open")
                        .queryParam("labels", "ai-in-progress")
                        .queryParam("per_page", "100")
                        .build(owner.trim(), repo.trim()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim())
                .retrieve()
                .body(GitHubIssueListItem[].class);

        if (response == null || response.length == 0) {
            return List.of();
        }

        List<ApprovedGitHubIssue> issues = new ArrayList<>();
        for (GitHubIssueListItem item : response) {
            if (item == null || item.getPullRequest() != null) {
                continue;
            }
            List<String> labels = extractLabelNames(item.getLabels());
            if (!labels.contains("ai-in-progress")) {
                continue;
            }
            ApprovedGitHubIssue normalized = new ApprovedGitHubIssue();
            normalized.setIssueNumber(item.getNumber());
            normalized.setTitle(item.getTitle());
            normalized.setBody(item.getBody());
            normalized.setLabels(labels);
            issues.add(normalized);
        }
        return issues;
    }

    private void safeAuditApprovedIssueSelection(List<ApprovedGitHubIssue> issues, String error) {
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("issues", issues != null ? issues : List.of());
            metadata.put("count", issues != null ? issues.size() : 0);
            fileAuditLogService.logStep5LifecycleEntry(
                    "approved-issue-selection",
                    "",
                    null,
                    metadata,
                    error
            );
        } catch (Exception ignored) {
            // Do not fail issue discovery due to audit logging.
        }
    }

    private String buildIssueBody(DecompositionStory story) {
        StringBuilder body = new StringBuilder();
        body.append("## Story ID\n");
        body.append(nullToEmpty(story.getStoryId())).append("\n\n");

        body.append("## Description\n");
        body.append(nullToEmpty(story.getDescription())).append("\n\n");

        body.append("## Acceptance Criteria\n");
        appendList(body, story.getAcceptanceCriteria());
        body.append("\n");

        body.append("## Affected Components\n");
        appendList(body, story.getAffectedComponents());
        body.append("\n");

        body.append("## Estimated Size\n");
        body.append(nullToEmpty(story.getEstimatedSize())).append("\n\n");

        body.append("## PR Safety\n");
        if (story.getPrSafety() == null) {
            body.append("- target: \n");
            body.append("- notes: \n");
        } else {
            body.append("- target: ").append(nullToEmpty(story.getPrSafety().getTarget())).append("\n");
            body.append("- notes: ").append(nullToEmpty(story.getPrSafety().getNotes())).append("\n");
        }
        return body.toString();
    }

    private void appendList(StringBuilder body, List<String> values) {
        if (values == null || values.isEmpty()) {
            body.append("- ").append("\n");
            return;
        }
        for (String value : values) {
            body.append("- ").append(nullToEmpty(value)).append("\n");
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private List<String> extractLabelNames(List<GitHubLabelResponse> labels) {
        if (labels == null || labels.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (GitHubLabelResponse label : labels) {
            if (label != null && StringUtils.hasText(label.getName())) {
                names.add(label.getName());
            }
        }
        return names;
    }

    static class CreateIssueRequest {
        @JsonProperty("title")
        private String title;

        @JsonProperty("body")
        private String body;

        @JsonProperty("labels")
        private List<String> labels;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public List<String> getLabels() {
            return labels;
        }

        public void setLabels(List<String> labels) {
            this.labels = labels;
        }
    }

    static class GitHubIssueResponse {
        @JsonProperty("number")
        private long number;

        @JsonProperty("html_url")
        private String htmlUrl;

        @JsonProperty("title")
        private String title;

        @JsonProperty("labels")
        private List<GitHubLabelResponse> labels;

        public long getNumber() {
            return number;
        }

        public void setNumber(long number) {
            this.number = number;
        }

        public String getHtmlUrl() {
            return htmlUrl;
        }

        public void setHtmlUrl(String htmlUrl) {
            this.htmlUrl = htmlUrl;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public List<GitHubLabelResponse> getLabels() {
            return labels;
        }

        public void setLabels(List<GitHubLabelResponse> labels) {
            this.labels = labels;
        }
    }

    static class GitHubIssueListItem {
        @JsonProperty("number")
        private long number;

        @JsonProperty("title")
        private String title;

        @JsonProperty("body")
        private String body;

        @JsonProperty("labels")
        private List<GitHubLabelResponse> labels;

        @JsonProperty("pull_request")
        private Object pullRequest;

        public long getNumber() {
            return number;
        }

        public void setNumber(long number) {
            this.number = number;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public List<GitHubLabelResponse> getLabels() {
            return labels;
        }

        public void setLabels(List<GitHubLabelResponse> labels) {
            this.labels = labels;
        }

        public Object getPullRequest() {
            return pullRequest;
        }

        public void setPullRequest(Object pullRequest) {
            this.pullRequest = pullRequest;
        }
    }

    static class GitHubLabelResponse {
        @JsonProperty("name")
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
