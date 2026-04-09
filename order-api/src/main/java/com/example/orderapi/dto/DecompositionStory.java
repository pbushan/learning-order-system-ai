package com.example.orderapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class DecompositionStory {
    @JsonProperty("storyId")
    @NotBlank(message = "storyId is required")
    private String storyId;

    @JsonProperty("title")
    @NotBlank(message = "title is required")
    private String title;

    @JsonProperty("description")
    @NotBlank(message = "description is required")
    private String description;

    @JsonProperty("acceptanceCriteria")
    @NotEmpty(message = "acceptanceCriteria is required")
    private List<String> acceptanceCriteria;

    @JsonProperty("affectedComponents")
    @NotEmpty(message = "affectedComponents is required")
    private List<String> affectedComponents;

    @JsonProperty("estimatedSize")
    private String estimatedSize;

    @JsonProperty("prSafety")
    @NotNull(message = "prSafety is required")
    @Valid
    private PrSafety prSafety;

    public String getStoryId() {
        return storyId;
    }

    public void setStoryId(String storyId) {
        this.storyId = storyId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getAcceptanceCriteria() {
        return acceptanceCriteria;
    }

    public void setAcceptanceCriteria(List<String> acceptanceCriteria) {
        this.acceptanceCriteria = acceptanceCriteria;
    }

    public List<String> getAffectedComponents() {
        return affectedComponents;
    }

    public void setAffectedComponents(List<String> affectedComponents) {
        this.affectedComponents = affectedComponents;
    }

    public String getEstimatedSize() {
        return estimatedSize;
    }

    public void setEstimatedSize(String estimatedSize) {
        this.estimatedSize = estimatedSize;
    }

    public PrSafety getPrSafety() {
        return prSafety;
    }

    public void setPrSafety(PrSafety prSafety) {
        this.prSafety = prSafety;
    }
}
