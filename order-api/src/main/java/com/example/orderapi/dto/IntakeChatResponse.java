package com.example.orderapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class IntakeChatResponse {
    @JsonProperty("reply")
    private String reply;

    @JsonProperty("intakeComplete")
    private boolean intakeComplete;

    @JsonProperty("structuredData")
    private StructuredIntakeData structuredData;

    @JsonProperty("requestId")
    private String requestId;

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
}
