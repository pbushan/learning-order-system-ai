package com.example.orderapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class GitHubIssueCreateResponse {
    public GitHubIssueCreateResponse() {
    }

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("issuesCreated")
    private boolean issuesCreated;

    @JsonProperty("issues")
    private List<GitHubIssueSummary> issues;

    @JsonProperty("error")
    private String error;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public boolean isIssuesCreated() {
        return issuesCreated;
    }

    public void setIssuesCreated(boolean issuesCreated) {
        this.issuesCreated = issuesCreated;
    }

    public List<GitHubIssueSummary> getIssues() {
        return issues;
    }

    public void setIssues(List<GitHubIssueSummary> issues) {
        this.issues = issues;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
