package com.example.orderapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class DecompositionResponse {
    @JsonProperty("requestId")
    @NotNull(message = "requestId is required")
    private String requestId;

    @JsonProperty("decompositionComplete")
    private boolean decompositionComplete;

    @JsonProperty("stories")
    @NotNull(message = "stories is required")
    @Valid
    private List<DecompositionStory> stories;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
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
