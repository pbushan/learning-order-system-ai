package com.example.orderapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class DecompositionResponse {
    public DecompositionResponse() {
    }

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("traceId")
    private String traceId;

    @JsonProperty("decompositionComplete")
    private boolean decompositionComplete;

    @JsonProperty("stories")
    private List<DecompositionStory> stories;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public boolean isDecompositionComplete() {
        return decompositionComplete;
    }

    public void setDecompositionComplete(boolean decompositionComplete) {
        this.decompositionComplete = decompositionComplete;
    }

    public List<DecompositionStory> getStories() {
        return stories;
    }

    public void setStories(List<DecompositionStory> stories) {
        this.stories = stories;
    }
}
