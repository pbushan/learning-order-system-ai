package com.example.orderapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class GitHubIssueSummary {
    public GitHubIssueSummary() {
    }

    @JsonProperty("storyId")
    private String storyId;

    @JsonProperty("issueNumber")
    private long issueNumber;

    @JsonProperty("issueUrl")
    private String issueUrl;

    @JsonProperty("title")
    private String title;

    @JsonProperty("labels")
    private List<String> labels;

    public String getStoryId() {
        return storyId;
    }

    public void setStoryId(String storyId) {
        this.storyId = storyId;
    }

    public long getIssueNumber() {
        return issueNumber;
    }

    public void setIssueNumber(long issueNumber) {
        this.issueNumber = issueNumber;
    }

    public String getIssueUrl() {
        return issueUrl;
    }

    public void setIssueUrl(String issueUrl) {
        this.issueUrl = issueUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }
}
