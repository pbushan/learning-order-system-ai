package com.example.orderapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class DecisionTraceResponse {

    @JsonProperty("traceId")
    private String traceId;

    @JsonProperty("events")
    private List<DecisionTraceEventResponse> events;

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

    public String buildConciseSummary() {
        if (events == null || events.isEmpty()) {
            return "No trace events available.";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Trace ");
        builder.append(traceId == null || traceId.isBlank() ? "unavailable" : traceId.trim());
        builder.append(": ");
        builder.append(events.get(0).buildConciseSummary());
        if (events.size() > 1) {
            builder.append(" (+").append(events.size() - 1).append(" more)");
        }
        return builder.toString();
    }
}
