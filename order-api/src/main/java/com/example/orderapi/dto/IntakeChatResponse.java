package com.example.orderapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class IntakeChatResponse {
    @JsonProperty("reply")
    @NotBlank(message = "reply is required")
    private String reply;

    @JsonProperty("intakeComplete")
    private boolean intakeComplete;

    @JsonProperty("structuredData")
    @NotNull(message = "structuredData is required")
    @Valid
    private StructuredIntakeData structuredData;

    @JsonProperty("requestId")
    @NotBlank(message = "requestId is required")
    private String requestId;

    @JsonProperty("traceId")
    private String traceId;

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public boolean isIntakeComplete() {
        return intakeComplete;
    }

    public void setIntakeComplete(boolean intakeComplete) {
        this.intakeComplete = intakeComplete;
    }

    public StructuredIntakeData getStructuredData() {
        return structuredData;
    }

    public void setStructuredData(StructuredIntakeData structuredData) {
        this.structuredData = structuredData;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
