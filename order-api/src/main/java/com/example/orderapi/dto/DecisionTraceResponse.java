package com.example.orderapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class DecisionTraceResponse {

    @JsonProperty("traceId")
    private String traceId;

    @JsonProperty("events")
    private List<DecisionTraceEventResponse> events;

    @JsonProperty("summary")
    private String summary;

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public List<DecisionTraceEventResponse> getEvents() {
        return events;
    }

    public void setEvents(List<DecisionTraceEventResponse> events) {
        this.events = events;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
