package com.example.orderapi.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.Nulls;

import java.util.ArrayList;
import java.util.List;

public class DecompositionResponse {
    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("decompositionComplete")
    private boolean decompositionComplete;

    @JsonProperty("stories")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private List<DecompositionStory> stories = new ArrayList<>();

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
