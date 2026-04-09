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
    @NotNull(message = "decompositionComplete is required")
    private Boolean decompositionComplete;

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

    public Boolean getDecompositionComplete() {
        return decompositionComplete;
    }

    public void setDecompositionComplete(Boolean decompositionComplete) {
        this.decompositionComplete = decompositionComplete;
    }

    public List<DecompositionStory> getStories() {
        return stories;
    }

    public void setStories(List<DecompositionStory> stories) {
        this.stories = stories;
    }
}
