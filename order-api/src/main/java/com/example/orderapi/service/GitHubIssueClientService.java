package com.example.orderapi.service;

import com.example.orderapi.dto.ApprovedGitHubIssue;
import com.example.orderapi.dto.DecompositionStory;
import com.example.orderapi.dto.GitHubIssueSummary;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GitHubIssueClientService {

    private static final Pattern ISSUE_URL_PATTERN = Pattern.compile("/issues/(\\d+)$");

    private final String token;
    private final String owner;
    private final String repo;
    private final String provider;
    private final FileAuditLogService fileAuditLogService;
    private final RestClient restClient;
    private final RestClient mcpRestClient;
    private final ObjectMapper objectMapper;

    public GitHubIssueClientService(@Value("${app.github.token:}") String token,
                                    @Value("${app.github.owner:}") String owner,
                                    @Value("${app.github.repo:}") String repo,
                                    @Value("${app.github.repository:}") String repository,
                                    @Value("${app.github.provider:mcp}") String provider,
                                    @Value("${app.github.mcp-base-url:http://github-mcp:8082}") String mcpBaseUrl,
                                    FileAuditLogService fileAuditLogService) {
        this.token = token;
        this.provider = StringUtils.hasText(provider) ? provider.trim().toLowerCase(Locale.ROOT) : "mcp";
        this.fileAuditLogService = fileAuditLogService;
        this.objectMapper = new ObjectMapper();

        String resolvedOwner = owner;
        String resolvedRepo = repo;
        if ((!StringUtils.hasText(resolvedOwner) || !StringUtils.hasText(resolvedRepo))
                && StringUtils.hasText(repository)
                && repository.contains("/")) {
            String[] parts = repository.trim().split("/", 2);
            if (!StringUtils.hasText(resolvedOwner)) {
                resolvedOwner = parts[0];
            }
            if (!StringUtils.hasText(resolvedRepo) && parts.length > 1) {
                resolvedRepo = parts[1];
            }
        }
        this.owner = resolvedOwner;
        this.repo = resolvedRepo;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(15000);

        this.restClient = RestClient.builder()
                .baseUrl("https://api.github.com")
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        this.mcpRestClient = RestClient.builder()
                .baseUrl(mcpBaseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json, text/event-stream")
                .build();
    }

    public GitHubIssueSummary createIssueForStory(String sourceType, DecompositionStory story) {
        validateTokenConfigured();
        validateRepositoryConfigured();
        if (story == null || !StringUtils.hasText(story.getTitle())) {
            throw new IllegalArgumentException("story.title is required");
        }
        if (!StringUtils.hasText(sourceType)) {
            throw new IllegalArgumentException("sourceType is required");
        }

        String normalizedSourceType = sourceType.trim().toLowerCase(Locale.ROOT);
        List<String> labels = List.of(
                "ai-generated",
                "needs-human-approval",
                normalizedSourceType,
                "portfolio"
        );

        if (useMcpProvider()) {
            return createIssueForStoryViaMcp(story, labels);
        }
        return createIssueForStoryDirect(story, labels);
    }

    public void addIssueLabel(long issueNumber, String label) {
        validateTokenConfigured();
        validateRepositoryConfigured();
        if (issueNumber <= 0) {
            throw new IllegalArgumentException("issueNumber must be > 0");
        }
        if (!StringUtils.hasText(label)) {
            throw new IllegalArgumentException("label is required");
        }

        if (useMcpProvider()) {
            addIssueLabelViaMcp(issueNumber, label.trim());
            return;
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
                    && (responseBody.contains("already_exists") || responseBody.toLowerCase(Locale.ROOT).contains("already exists"))) {
                // Treat label-already-present responses as idempotent success for pickup.
                return;
            }
            throw ex;
        }
    }

    public boolean removeIssueLabel(long issueNumber, String label) {
        validateTokenConfigured();
        validateRepositoryConfigured();
        if (issueNumber <= 0) {
            throw new IllegalArgumentException("issueNumber must be > 0");
        }
        if (!StringUtils.hasText(label)) {
            throw new IllegalArgumentException("label is required");
        }

        if (useMcpProvider()) {
            return removeIssueLabelViaMcp(issueNumber, label.trim());
        }

        int attempts = 0;
        while (attempts < 2) {
            attempts++;
            try {
                restClient.delete()
                        .uri("/repos/{owner}/{repo}/issues/{issueNumber}/labels/{label}",
                                owner.trim(), repo.trim(), issueNumber, label.trim())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim())
                        .retrieve()
                        .toBodilessEntity();
                return true;
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                if (status == 404) {
                    return false;
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
        return false;
    }

    public boolean removeIssueLabelCaseInsensitive(long issueNumber, String label) {
        if (issueNumber <= 0) {
            throw new IllegalArgumentException("issueNumber must be > 0");
        }
        if (!StringUtils.hasText(label)) {
            throw new IllegalArgumentException("label is required");
        }
        List<String> labels = fetchIssueLabelNamesWithRetry(issueNumber);
        if (labels.isEmpty()) {
            return false;
        }
        String target = label.trim().toLowerCase(Locale.ROOT);
        for (String current : labels) {
            if (StringUtils.hasText(current) && current.trim().toLowerCase(Locale.ROOT).equals(target)) {
                boolean removed = removeIssueLabel(issueNumber, current);
                if (removed) {
                    return true;
                }
                // Label set may have changed between fetch and delete; retry once with a fresh view.
                List<String> refreshed = fetchIssueLabelNamesWithRetry(issueNumber);
                for (String refreshedLabel : refreshed) {
                    if (StringUtils.hasText(refreshedLabel)
                            && refreshedLabel.trim().toLowerCase(Locale.ROOT).equals(target)) {
                        return removeIssueLabel(issueNumber, refreshedLabel);
                    }
                }
                return false;
            }
        }
        return false;
    }

    public List<ApprovedGitHubIssue> discoverApprovedIssues() {
        validateTokenConfigured();
        validateRepositoryConfigured();

        List<ApprovedGitHubIssue> issues = new ArrayList<>();
        String error = null;

        try {
            if (useMcpProvider()) {
                issues = discoverIssuesViaMcp(List.of("approved-for-dev"), true);
                return issues;
            }

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
                List<String> normalizedLabels = normalizeLabels(labels);
                if (normalizedLabels.contains("ai-in-progress")) {
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
        validateTokenConfigured();
        validateRepositoryConfigured();

        if (useMcpProvider()) {
            return discoverIssuesViaMcp(List.of("ai-in-progress"), false);
        }

        GitHubIssueListItem[] response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/issues")
                        .queryParam("state", "open")
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
            List<String> normalizedLabels = normalizeLabels(labels);
            if (!normalizedLabels.contains("ai-in-progress")) {
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

    public List<Map<String, Object>> listOpenPullRequests() {
        validateTokenConfigured();
        validateRepositoryConfigured();
        if (!useMcpProvider()) {
            throw new IllegalStateException("Step 6 pull request polling requires app.github.provider=mcp");
        }

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("owner", owner.trim());
        arguments.put("repo", repo.trim());
        arguments.put("state", "open");
        arguments.put("perPage", 100);
        JsonNode result = callMcpTool("list_pull_requests", arguments);
        JsonNode payload = parseJson(extractMcpContentText(result));
        JsonNode pullRequestsNode = payload;
        if (!pullRequestsNode.isArray()) {
            pullRequestsNode = payload.path("pullRequests");
        }
        if (!pullRequestsNode.isArray()) {
            return List.of();
        }

        List<Map<String, Object>> pulls = new ArrayList<>();
        for (JsonNode pullNode : pullRequestsNode) {
            if (pullNode == null || pullNode.isNull()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("number", pullNode.path("number").asLong(0));
            item.put("title", pullNode.path("title").asText(""));
            item.put("body", pullNode.path("body").asText(""));
            item.put("state", pullNode.path("state").asText(""));
            String url = pullNode.path("url").asText("");
            if (!StringUtils.hasText(url)) {
                url = pullNode.path("html_url").asText("");
            }
            item.put("url", url);
            String headRefName = pullNode.path("headRefName").asText("");
            if (!StringUtils.hasText(headRefName)) {
                headRefName = pullNode.path("head").path("ref").asText("");
            }
            item.put("headRefName", headRefName);
            String headRefOid = pullNode.path("headRefOid").asText("");
            if (!StringUtils.hasText(headRefOid)) {
                headRefOid = pullNode.path("head").path("sha").asText("");
            }
            item.put("headRefOid", headRefOid);
            String author = pullNode.path("author").path("login").asText("");
            if (!StringUtils.hasText(author)) {
                author = pullNode.path("author").asText("");
            }
            if (!StringUtils.hasText(author)) {
                author = pullNode.path("user").path("login").asText("");
            }
            item.put("author", author);
            pulls.add(item);
        }
        return pulls;
    }

    public List<Map<String, Object>> getPullRequestReviews(long pullNumber) {
        validateTokenConfigured();
        validateRepositoryConfigured();
        if (pullNumber <= 0) {
            return List.of();
        }
        if (!useMcpProvider()) {
            throw new IllegalStateException("Step 6 pull request review ingestion requires app.github.provider=mcp");
        }

        JsonNode payload = readPullRequestViaMcp("get_reviews", pullNumber);
        JsonNode source = payload;
        if (payload.isObject() && payload.path("reviews").isArray()) {
            source = payload.path("reviews");
        }
        if (!source.isArray()) {
            return List.of();
        }

        List<Map<String, Object>> reviews = new ArrayList<>();
        for (JsonNode reviewNode : source) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", reviewNode.path("id").asText(""));
            String author = reviewNode.path("author").path("login").asText("");
            if (!StringUtils.hasText(author)) {
                author = reviewNode.path("user").path("login").asText("");
            }
            item.put("author", author);
            item.put("state", reviewNode.path("state").asText(""));
            String body = extractCommentBody(reviewNode);
            item.put("body", body);
            String url = reviewNode.path("url").asText("");
            if (!StringUtils.hasText(url)) {
                url = reviewNode.path("html_url").asText("");
            }
            item.put("url", url);
            String submittedAt = reviewNode.path("submittedAt").asText("");
            if (!StringUtils.hasText(submittedAt)) {
                submittedAt = reviewNode.path("submitted_at").asText("");
            }
            item.put("submittedAt", submittedAt);
            reviews.add(item);
        }
        return reviews;
    }

    public List<Map<String, Object>> getPullRequestReviewComments(long pullNumber) {
        validateTokenConfigured();
        validateRepositoryConfigured();
        if (pullNumber <= 0) {
            return List.of();
        }
        if (!useMcpProvider()) {
            throw new IllegalStateException("Step 6 review comment ingestion requires app.github.provider=mcp");
        }

        JsonNode payload = readPullRequestViaMcp("get_review_comments", pullNumber);
        List<Map<String, Object>> comments = new ArrayList<>();
        JsonNode threads = payload.path("review_threads");
        if (threads.isArray()) {
            for (JsonNode thread : threads) {
                JsonNode threadComments = thread.path("comments");
                if (!threadComments.isArray()) {
                    continue;
                }
                for (JsonNode commentNode : threadComments) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", commentNode.path("id").asText(""));
                    String author = commentNode.path("author").path("login").asText("");
                    if (!StringUtils.hasText(author)) {
                        author = commentNode.path("user").path("login").asText("");
                    }
                    item.put("author", author);
                    String body = extractCommentBody(commentNode);
                    item.put("body", body);
                    String url = commentNode.path("url").asText("");
                    if (!StringUtils.hasText(url)) {
                        url = commentNode.path("html_url").asText("");
                    }
                    item.put("url", url);
                    item.put("path", commentNode.path("path").asText(""));
                    item.put("line", commentNode.path("line").asInt(0));
                    String createdAt = commentNode.path("createdAt").asText("");
                    if (!StringUtils.hasText(createdAt)) {
                        createdAt = commentNode.path("created_at").asText("");
                    }
                    item.put("createdAt", createdAt);
                    comments.add(item);
                }
            }
            return comments;
        }

        JsonNode directComments = payload;
        if (payload.isObject() && payload.path("comments").isArray()) {
            directComments = payload.path("comments");
        }
        if (!directComments.isArray()) {
            return comments;
        }
        for (JsonNode commentNode : directComments) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", commentNode.path("id").asText(""));
            String author = commentNode.path("author").path("login").asText("");
            if (!StringUtils.hasText(author)) {
                author = commentNode.path("user").path("login").asText("");
            }
            item.put("author", author);
            String body = extractCommentBody(commentNode);
            item.put("body", body);
            String url = commentNode.path("url").asText("");
            if (!StringUtils.hasText(url)) {
                url = commentNode.path("html_url").asText("");
            }
            item.put("url", url);
            item.put("path", commentNode.path("path").asText(""));
            item.put("line", commentNode.path("line").asInt(0));
            String createdAt = commentNode.path("createdAt").asText("");
            if (!StringUtils.hasText(createdAt)) {
                createdAt = commentNode.path("created_at").asText("");
            }
            item.put("createdAt", createdAt);
            comments.add(item);
        }
        return comments;
    }

    public List<Map<String, Object>> getPullRequestIssueComments(long pullNumber) {
        validateTokenConfigured();
        validateRepositoryConfigured();
        if (pullNumber <= 0) {
            return List.of();
        }
        if (!useMcpProvider()) {
            throw new IllegalStateException("Step 6 issue comment ingestion requires app.github.provider=mcp");
        }

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("method", "get_comments");
        arguments.put("owner", owner.trim());
        arguments.put("repo", repo.trim());
        arguments.put("issue_number", pullNumber);
        JsonNode result = callMcpTool("issue_read", arguments);
        JsonNode payload = parseJson(extractMcpContentText(result));
        JsonNode source = payload;
        if (payload.isObject() && payload.path("comments").isArray()) {
            source = payload.path("comments");
        }
        if (!source.isArray()) {
            return List.of();
        }

        List<Map<String, Object>> comments = new ArrayList<>();
        for (JsonNode commentNode : source) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", commentNode.path("id").asText(""));
            String author = commentNode.path("author").path("login").asText("");
            if (!StringUtils.hasText(author)) {
                author = commentNode.path("user").path("login").asText("");
            }
            item.put("author", author);
            String body = extractCommentBody(commentNode);
            item.put("body", body);
            String url = commentNode.path("url").asText("");
            if (!StringUtils.hasText(url)) {
                url = commentNode.path("html_url").asText("");
            }
            item.put("url", url);
            String createdAt = commentNode.path("createdAt").asText("");
            if (!StringUtils.hasText(createdAt)) {
                createdAt = commentNode.path("created_at").asText("");
            }
            item.put("createdAt", createdAt);
            comments.add(item);
        }
        return comments;
    }

    private String extractCommentBody(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        String body = node.path("body").asText("");
        if (!StringUtils.hasText(body)) {
            body = node.path("bodyText").asText("");
        }
        if (!StringUtils.hasText(body)) {
            body = node.path("content").asText("");
        }
        if (!StringUtils.hasText(body)) {
            body = node.path("text").asText("");
        }
        if (!StringUtils.hasText(body)) {
            body = node.toString();
        }
        return body;
    }

    public void addPullRequestComment(long pullNumber, String body) {
        validateTokenConfigured();
        validateRepositoryConfigured();
        if (pullNumber <= 0) {
            throw new IllegalArgumentException("pullNumber must be > 0");
        }
        if (!StringUtils.hasText(body)) {
            throw new IllegalArgumentException("body is required");
        }

        if (!useMcpProvider()) {
            throw new IllegalStateException("Step 6 pull request commenting requires app.github.provider=mcp");
        }

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("owner", owner.trim());
        arguments.put("repo", repo.trim());
        arguments.put("issue_number", pullNumber);
        arguments.put("body", body.trim());
        callMcpTool("add_issue_comment", arguments);
    }

    public void updatePullRequest(long pullNumber, String title, String body) {
        validateTokenConfigured();
        validateRepositoryConfigured();
        if (pullNumber <= 0) {
            throw new IllegalArgumentException("pullNumber must be > 0");
        }

        Map<String, Object> updateFields = new LinkedHashMap<>();
        if (StringUtils.hasText(title)) {
            updateFields.put("title", title.trim());
        }
        if (body != null) {
            updateFields.put("body", body);
        }
        if (updateFields.isEmpty()) {
            return;
        }
        if (!useMcpProvider()) {
            throw new IllegalStateException("Step 6 pull request updates require app.github.provider=mcp");
        }

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("owner", owner.trim());
        arguments.put("repo", repo.trim());
        arguments.put("pullNumber", pullNumber);
        arguments.putAll(updateFields);
        callMcpTool("update_pull_request", arguments);
    }

    public Set<String> fetchIssueLabelNamesCaseInsensitive(long issueNumber) {
        List<String> labels = fetchIssueLabelNamesWithRetry(issueNumber);
        if (labels.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new HashSet<>();
        for (String label : labels) {
            if (StringUtils.hasText(label)) {
                normalized.add(label.trim().toLowerCase(Locale.ROOT));
            }
        }
        return normalized;
    }

    private GitHubIssueSummary createIssueForStoryDirect(DecompositionStory story, List<String> labels) {
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

    private GitHubIssueSummary createIssueForStoryViaMcp(DecompositionStory story, List<String> labels) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("method", "create");
        arguments.put("owner", owner.trim());
        arguments.put("repo", repo.trim());
        arguments.put("title", story.getTitle().trim());
        arguments.put("body", buildIssueBody(story));
        arguments.put("labels", labels);

        JsonNode result = callMcpTool("issue_write", arguments);
        String contentText = extractMcpContentText(result);
        JsonNode issueResponse = parseJson(contentText);

        String issueUrl = issueResponse.path("url").asText("");
        long issueNumber = extractIssueNumber(issueUrl);
        if (issueNumber <= 0) {
            throw new IllegalStateException("GitHub issue creation failed: MCP response missing issue number.");
        }

        GitHubIssueSummary summary = new GitHubIssueSummary();
        summary.setIssueNumber(issueNumber);
        summary.setIssueUrl(issueUrl);
        summary.setTitle(story.getTitle().trim());
        summary.setLabels(labels);
        return summary;
    }

    private void addIssueLabelViaMcp(long issueNumber, String label) {
        List<String> labels = fetchIssueLabelNamesWithRetry(issueNumber);
        for (String existing : labels) {
            if (StringUtils.hasText(existing)
                    && existing.trim().equalsIgnoreCase(label)) {
                return;
            }
        }
        List<String> updated = new ArrayList<>(labels);
        updated.add(label);
        updateIssueLabelsViaMcp(issueNumber, updated);
    }

    private boolean removeIssueLabelViaMcp(long issueNumber, String label) {
        List<String> labels = fetchIssueLabelNamesWithRetry(issueNumber);
        if (labels.isEmpty()) {
            return false;
        }
        List<String> updated = new ArrayList<>();
        boolean removed = false;
        for (String existing : labels) {
            if (StringUtils.hasText(existing)
                    && existing.trim().equals(label)) {
                removed = true;
                continue;
            }
            updated.add(existing);
        }
        if (!removed) {
            return false;
        }
        updateIssueLabelsViaMcp(issueNumber, updated);
        return true;
    }

    private void updateIssueLabelsViaMcp(long issueNumber, List<String> labels) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("method", "update");
        arguments.put("owner", owner.trim());
        arguments.put("repo", repo.trim());
        arguments.put("issue_number", issueNumber);
        arguments.put("labels", labels);
        callMcpTool("issue_write", arguments);
    }

    private JsonNode callMcpTool(String toolName, Map<String, Object> arguments) {
        RuntimeException last = null;
        long waitMillis = 250L;
        for (int attempt = 1; attempt <= 4; attempt++) {
            try {
                String sessionId = initializeMcpSession();
                sendMcpInitializedNotification(sessionId);

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("jsonrpc", "2.0");
                payload.put("id", 2);
                payload.put("method", "tools/call");
                payload.put("params", Map.of("name", toolName, "arguments", arguments));

                String responseBody = mcpRestClient.post()
                        .uri("/")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim())
                        .header("Mcp-Session-Id", sessionId)
                        .body(payload)
                        .retrieve()
                        .body(String.class);

                JsonNode envelope = parseMcpEnvelope(responseBody);
                JsonNode result = envelope.path("result");
                if (result.path("isError").asBoolean(false)) {
                    String message = extractMcpContentText(result);
                    throw new IllegalStateException("GitHub MCP call failed: " + message);
                }
                if (result.isMissingNode()) {
                    throw new IllegalStateException("GitHub MCP call failed: empty result.");
                }
                return result;
            } catch (RuntimeException ex) {
                last = ex;
                if (attempt >= 4 || !isRetryableMcpError(ex)) {
                    throw ex;
                }
                sleepQuietly(waitMillis);
                waitMillis = Math.min(waitMillis * 2, 2000L);
            }
        }
        throw last != null ? last : new IllegalStateException("GitHub MCP call failed.");
    }

    private String initializeMcpSession() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 1);
        payload.put("method", "initialize");
        payload.put("params", Map.of(
                "protocolVersion", "2025-03-26",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "order-api", "version", "1.0")
        ));

        ResponseEntity<String> response = mcpRestClient.post()
                .uri("/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim())
                .body(payload)
                .retrieve()
                .toEntity(String.class);

        String sessionId = response.getHeaders().getFirst("Mcp-Session-Id");
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalStateException("GitHub MCP session initialization failed: missing Mcp-Session-Id header.");
        }
        return sessionId;
    }

    private void sendMcpInitializedNotification(String sessionId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("method", "notifications/initialized");
        payload.put("params", Map.of());

        mcpRestClient.post()
                .uri("/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim())
                .header("Mcp-Session-Id", sessionId)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    private JsonNode parseMcpEnvelope(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            throw new IllegalStateException("GitHub MCP call failed: empty response body.");
        }

        String trimmed = rawBody.trim();
        if (trimmed.startsWith("{")) {
            return parseJson(trimmed);
        }

        for (String line : rawBody.split("\\n")) {
            if (line.startsWith("data: ")) {
                return parseJson(line.substring(6).trim());
            }
        }

        throw new IllegalStateException("GitHub MCP call failed: unsupported response format.");
    }

    private String extractMcpContentText(JsonNode resultNode) {
        JsonNode contentArray = resultNode.path("content");
        if (!contentArray.isArray() || contentArray.isEmpty()) {
            return "";
        }
        JsonNode textNode = contentArray.get(0).path("text");
        return textNode.asText("");
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse GitHub response payload.", ex);
        }
    }

    private long extractIssueNumber(String issueUrl) {
        if (!StringUtils.hasText(issueUrl)) {
            return 0L;
        }
        Matcher matcher = ISSUE_URL_PATTERN.matcher(issueUrl.trim());
        if (!matcher.find()) {
            return 0L;
        }
        return Long.parseLong(matcher.group(1));
    }

    private List<ApprovedGitHubIssue> discoverIssuesViaMcp(List<String> labels, boolean excludeInProgress) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("owner", owner.trim());
        arguments.put("repo", repo.trim());
        arguments.put("state", "OPEN");
        arguments.put("perPage", 100);
        if (labels != null && !labels.isEmpty()) {
            arguments.put("labels", labels);
        }

        JsonNode result = callMcpTool("list_issues", arguments);
        String contentText = extractMcpContentText(result);
        JsonNode payload = parseJson(contentText);
        JsonNode issuesNode = payload.path("issues");

        if (!issuesNode.isArray() || issuesNode.isEmpty()) {
            return List.of();
        }

        List<ApprovedGitHubIssue> issues = new ArrayList<>();
        for (JsonNode issueNode : issuesNode) {
            if (issueNode == null || issueNode.path("pull_request").isObject()) {
                continue;
            }
            List<String> issueLabels = extractMcpLabelNames(issueNode.path("labels"));
            List<String> normalizedLabels = normalizeLabels(issueLabels);
            if (excludeInProgress && normalizedLabels.contains("ai-in-progress")) {
                continue;
            }

            ApprovedGitHubIssue normalized = new ApprovedGitHubIssue();
            normalized.setIssueNumber(issueNode.path("number").asLong(0));
            normalized.setTitle(issueNode.path("title").asText(""));
            normalized.setBody(issueNode.path("body").asText(""));
            normalized.setLabels(issueLabels);
            if (normalized.getIssueNumber() > 0) {
                issues.add(normalized);
            }
        }
        return issues;
    }

    private JsonNode readPullRequestViaMcp(String method, long pullNumber) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("method", method);
        arguments.put("owner", owner.trim());
        arguments.put("repo", repo.trim());
        arguments.put("pullNumber", pullNumber);
        JsonNode result = callMcpTool("pull_request_read", arguments);
        return parseJson(extractMcpContentText(result));
    }

    private List<String> extractMcpLabelNames(JsonNode labelsNode) {
        if (labelsNode == null || labelsNode.isMissingNode() || !labelsNode.isArray()) {
            return List.of();
        }
        List<String> labels = new ArrayList<>();
        for (JsonNode labelNode : labelsNode) {
            if (labelNode == null || labelNode.isNull()) {
                continue;
            }
            if (labelNode.isTextual()) {
                if (StringUtils.hasText(labelNode.asText())) {
                    labels.add(labelNode.asText());
                }
                continue;
            }
            if (labelNode.isObject() && StringUtils.hasText(labelNode.path("name").asText())) {
                labels.add(labelNode.path("name").asText());
            }
        }
        return labels;
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

    private List<String> fetchIssueLabelNames(long issueNumber) {
        if (useMcpProvider()) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("method", "get");
            arguments.put("owner", owner.trim());
            arguments.put("repo", repo.trim());
            arguments.put("issue_number", issueNumber);
            JsonNode result = callMcpTool("issue_read", arguments);
            JsonNode payload = parseJson(extractMcpContentText(result));
            return extractMcpLabelNames(payload.path("labels"));
        }

        GitHubIssueListItem issue = restClient.get()
                .uri("/repos/{owner}/{repo}/issues/{issueNumber}", owner.trim(), repo.trim(), issueNumber)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim())
                .retrieve()
                .body(GitHubIssueListItem.class);
        if (issue == null) {
            return List.of();
        }
        return extractLabelNames(issue.getLabels());
    }

    private List<String> fetchIssueLabelNamesWithRetry(long issueNumber) {
        int attempts = 0;
        while (attempts < 2) {
            attempts++;
            try {
                return fetchIssueLabelNames(issueNumber);
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                boolean transientFailure = status == 429 || (status >= 500 && status <= 599);
                if (!transientFailure || attempts >= 2) {
                    throw ex;
                }
                sleepQuietly(300);
            } catch (ResourceAccessException ex) {
                if (attempts >= 2) {
                    throw ex;
                }
                sleepQuietly(300);
            }
        }
        return List.of();
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for GitHub retry.", interruptedException);
        }
    }

    private boolean isRetryableMcpError(RuntimeException ex) {
        if (ex instanceof ResourceAccessException) {
            return true;
        }
        if (ex instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            return status == 429 || (status >= 500 && status <= 599);
        }
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String text = message.toLowerCase(Locale.ROOT);
        return text.contains("connection refused")
                || text.contains("connect timed out")
                || text.contains("read timed out")
                || text.contains("unexpected end of file")
                || text.contains("empty response body");
    }

    private List<String> normalizeLabels(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String label : labels) {
            if (!StringUtils.hasText(label)) {
                continue;
            }
            normalized.add(label.trim().toLowerCase(Locale.ROOT));
        }
        return normalized;
    }

    private void validateTokenConfigured() {
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("GitHub token is not configured. Set app.github.token or APP_GITHUB_TOKEN.");
        }
    }

    private void validateRepositoryConfigured() {
        if (!StringUtils.hasText(owner) || !StringUtils.hasText(repo)) {
            throw new IllegalStateException("GitHub repository is not configured. Set app.github.owner/app.github.repo or APP_GITHUB_REPOSITORY.");
        }
    }

    private boolean useMcpProvider() {
        return "mcp".equals(provider);
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
