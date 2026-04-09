package com.example.orderapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PrSafety {
    public PrSafety() {
    }

    @JsonProperty("target")
    private String target;

    @JsonProperty("notes")
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
