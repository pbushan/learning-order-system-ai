package com.example.orderapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class PrSafety {
    public PrSafety() {
    }

    @JsonProperty("target")
    @NotBlank(message = "target is required")
    private String target;

    @JsonProperty("notes")
    @NotBlank(message = "notes is required")
    private String notes;

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
