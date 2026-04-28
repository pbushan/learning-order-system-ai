package com.example.orderapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;

import java.util.List;

public class DecisionTraceResponse {

    @JsonProperty("traceId")
    private String traceId;

    @JsonProperty("events")
    private List<DecisionTraceEventResponse> events;
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("summary")
    private String summary;

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public List<DecisionTraceEventResponse> getEvents() {
        if (events == null) {
            events = new ArrayList<>();
        }
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
