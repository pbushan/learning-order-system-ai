package com.example.orderapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class DecisionTraceEventResponse {

    @JsonProperty("traceId")
    private String traceId;

    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("correlationId")
    private String correlationId;

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("status")
    private String status;

    @JsonProperty("actor")
    private String actor;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("decisionMetadata")
    private Map<String, Object> decisionMetadata;

    @JsonProperty("inputSummary")
    private Map<String, Object> inputSummary;

    @JsonProperty("artifactSummary")
    private Map<String, Object> artifactSummary;

    @JsonProperty("governanceMetadata")
    private Map<String, Object> governanceMetadata;

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Map<String, Object> getDecisionMetadata() {
        return decisionMetadata;
    }

    public void setDecisionMetadata(Map<String, Object> decisionMetadata) {
        this.decisionMetadata = decisionMetadata;
    }

    public Map<String, Object> getInputSummary() {
        return inputSummary;
    }

    public void setInputSummary(Map<String, Object> inputSummary) {
        this.inputSummary = inputSummary;
    }

    public Map<String, Object> getArtifactSummary() {
        return artifactSummary;
    }

    public void setArtifactSummary(Map<String, Object> artifactSummary) {
        this.artifactSummary = artifactSummary;
    }

    public Map<String, Object> getGovernanceMetadata() {
        return governanceMetadata;
    }

    public void setGovernanceMetadata(Map<String, Object> governanceMetadata) {
        this.governanceMetadata = governanceMetadata;
    }
}
