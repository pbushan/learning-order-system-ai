package com.example.orderapi.service;

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

import java.util.ArrayList;
import java.util.List;

@Service
public class GitHubIssueClientService {

    private final String token;
    private final String owner;
    private final String repo;
    private final RestClient restClient;

    public GitHubIssueClientService(@Value("${app.github.token:}") String token,
                                    @Value("${app.github.owner:}") String owner,
                                    @Value("${app.github.repo:}") String repo) {
        this.token = token;
        this.owner = owner;
        this.repo = repo;

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
